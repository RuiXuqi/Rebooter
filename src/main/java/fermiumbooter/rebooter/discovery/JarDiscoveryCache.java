package fermiumbooter.rebooter.discovery;

import com.google.common.base.Stopwatch;
import fermiumbooter.rebooter.Reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class JarDiscoveryCache {
    private static final String ENABLED_PROPERTY = "rebooter.discoveryCache";
    // RBTDISC Rebooter Discovery
    // Bump when format updated
    private static final long MAGIC = 0x5242544449534302L;
    private static final int DIGEST_LENGTH = 32;
    private static final int MAX_FILE_SIZE = 16 * 1024 * 1024;
    private static final int MAX_PAYLOAD_SIZE = MAX_FILE_SIZE - 4096;
    private static final int MAX_JARS = 4096;
    private static final int MAX_CONFIG_CLASSES_PER_JAR = 4096;
    private static final int MAX_CONFIG_CLASSES_TOTAL = 100_000;
    private static final int MAX_MOD_IDS_PER_JAR = 4096;
    private static final int MAX_MOD_IDS_TOTAL = 300_000;
    private static final int MAX_STRING_BYTES = 65_535;

    private final Path file;
    private final Map<String, Entry> loaded;
    private final Map<String, Entry> current = new LinkedHashMap<>();
    private final boolean enabled;
    private final byte[] scanProfileDigest;
    private boolean dirty;

    private JarDiscoveryCache(
            Path file,
            Map<String, Entry> loaded,
            boolean dirty,
            boolean enabled,
            byte[] scanProfileDigest) {
        this.file = file;
        this.loaded = loaded;
        this.dirty = dirty;
        this.enabled = enabled;
        this.scanProfileDigest = scanProfileDigest;
    }

    static JarDiscoveryCache load(File gameDirectory) {
        return load(gameDirectory, "");
    }

    static JarDiscoveryCache load(File gameDirectory, String scanProfile) {
        Objects.requireNonNull(scanProfile, "scanProfile");
        byte[] scanProfileDigest = sha256().digest(scanProfile.getBytes(StandardCharsets.UTF_8));
        Path file = gameDirectory.toPath().resolve("cache/rebooter/jar-discovery.bin");
        if (!cacheEnabled()) {
            return new JarDiscoveryCache(file, Collections.emptyMap(), false, false, scanProfileDigest);
        }
        if (!Files.isRegularFile(file)) {
            return new JarDiscoveryCache(file, Collections.emptyMap(), true, true, scanProfileDigest);
        }
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            long size = Files.size(file);
            if (size <= 0 || size > MAX_FILE_SIZE) {
                throw new IOException("cache size is outside the accepted range");
            }
            Map<String, Entry> loaded = decode(Files.readAllBytes(file), scanProfileDigest);
            Reference.LOGGER.info("Loaded JAR discovery cache with {} JAR entries in {} ms",
                    loaded.size(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
            return new JarDiscoveryCache(file, loaded, false, true, scanProfileDigest);
        } catch (IOException | RuntimeException e) {
            Reference.LOGGER.debug("Ignoring invalid JAR discovery cache {}", file, e);
            return new JarDiscoveryCache(file, Collections.emptyMap(), true, true, scanProfileDigest);
        }
    }

    /**
     * File state is paired with a full-file SHA-256 fingerprint before cached discovery data is reused.
     */
    static FileStamp stamp(File source) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                source.toPath(), BasicFileAttributes.class);
        Object fileKey = attributes.fileKey();
        return new FileStamp(
                attributes.size(),
                attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS),
                attributes.creationTime().to(TimeUnit.NANOSECONDS),
                fileKey == null ? "" : fileKey.toString());
    }

    boolean isEnabled() {
        return this.enabled;
    }

    boolean hasLoadedEntry(File source) {
        return this.enabled && this.loaded.containsKey(source.getAbsolutePath());
    }

    CachedData lookup(File source, FileStamp stamp, byte[] fingerprint) {
        if (!this.enabled) return null;
        String path = source.getAbsolutePath();
        Entry entry = this.loaded.get(path);
        if (entry == null || !Arrays.equals(entry.fingerprint, fingerprint)) return null;
        Entry refreshed = entry.withStamp(stamp);
        this.current.put(path, refreshed);
        this.dirty |= refreshed != entry;
        return refreshed.data();
    }

    void record(
            File source,
            FileStamp stamp,
            byte[] fingerprint,
            List<String> configClasses,
            String metadataModId,
            Set<String> annotationModIds,
            Set<String> mappedModIds,
            Set<String> launcherModIds) {
        if (!this.enabled) return;
        String path = source.getAbsolutePath();
        this.current.put(path, new Entry(
                stamp,
                fingerprint.clone(),
                immutable(configClasses),
                metadataModId,
                immutableSorted(annotationModIds),
                immutableSorted(mappedModIds),
                immutableSorted(launcherModIds)));
        this.dirty = true;
    }

    void save() {
        if (!this.enabled || (!this.dirty && this.current.size() == this.loaded.size())) return;
        Stopwatch stopwatch = Stopwatch.createStarted();
        Path parent = this.file.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            byte[] bytes = encode(this.current, this.scanProfileDigest);
            temporary = Files.createTempFile(parent, "jar-discovery-", ".tmp");
            try (FileOutputStream fileOutput = new FileOutputStream(temporary.toFile())) {
                fileOutput.write(bytes);
                fileOutput.getFD().sync();
            }
            Files.move(
                    temporary,
                    this.file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
            Reference.LOGGER.info("Rebuilt JAR discovery cache with {} JAR entries in {} ms",
                    this.current.size(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
        } catch (IOException | RuntimeException e) {
            Reference.LOGGER.warn("Unable to update JAR discovery cache {}", this.file, e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A stale temporary file is harmless and can be replaced on the next launch.
                }
            }
        }
    }

    static byte[] contentFingerprint(File source) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(source.toPath())) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }

    private static byte[] encode(Map<String, Entry> entries, byte[] scanProfileDigest) throws IOException {
        checkCount(entries.size(), MAX_JARS, "JAR count");
        int totalConfigClasses = 0;
        int totalModIds = 0;
        for (Entry entry : entries.values()) {
            checkCount(
                    entry.configClasses.size(),
                    MAX_CONFIG_CLASSES_PER_JAR,
                    "config class count");
            totalConfigClasses = checkedTotal(
                    totalConfigClasses,
                    entry.configClasses.size(),
                    MAX_CONFIG_CLASSES_TOTAL,
                    "total config class count");
            totalModIds = checkModIds(entry.annotationModIds, totalModIds, "annotation mod ID");
            totalModIds = checkModIds(entry.mappedModIds, totalModIds, "mapped mod ID");
            totalModIds = checkModIds(entry.launcherModIds, totalModIds, "launcher mod ID");
        }
        BoundedByteArrayOutputStream payloadBytes = new BoundedByteArrayOutputStream(MAX_PAYLOAD_SIZE);
        try (DataOutputStream payload = new DataOutputStream(payloadBytes)) {
            payload.writeInt(entries.size());
            for (Map.Entry<String, Entry> cached : entries.entrySet()) {
                writeString(payload, cached.getKey());
                Entry entry = cached.getValue();
                payload.writeLong(entry.stamp.length);
                payload.writeLong(entry.stamp.lastModifiedNanos);
                payload.writeLong(entry.stamp.creationTimeNanos);
                writeString(payload, entry.stamp.fileKey);
                payload.write(entry.fingerprint);
                writeString(payload, entry.metadataModId == null ? "" : entry.metadataModId);
                writeStrings(payload, entry.configClasses);
                writeStrings(payload, entry.annotationModIds);
                writeStrings(payload, entry.mappedModIds);
                writeStrings(payload, entry.launcherModIds);
            }
        }
        byte[] payload = payloadBytes.toByteArray();
        byte[] digest = sha256().digest(payload);
        ByteArrayOutputStream fileBytes = new ByteArrayOutputStream(payload.length + 128);
        try (DataOutputStream output = new DataOutputStream(fileBytes)) {
            output.writeLong(MAGIC);
            writeString(output, Reference.VERSION);
            output.write(scanProfileDigest);
            output.writeInt(payload.length);
            output.write(digest);
            output.write(payload);
        }
        return fileBytes.toByteArray();
    }

    private static Map<String, Entry> decode(byte[] bytes, byte[] expectedScanProfileDigest) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readLong() != MAGIC) {
                throw new IOException("incorrect cache magic");
            }
            if (!Reference.VERSION.equals(readString(input))) {
                throw new IOException("cache belongs to a different Rebooter version");
            }
            byte[] cachedScanProfileDigest = new byte[DIGEST_LENGTH];
            input.readFully(cachedScanProfileDigest);
            if (!MessageDigest.isEqual(expectedScanProfileDigest, cachedScanProfileDigest)) {
                throw new IOException("cache belongs to a different class scan profile");
            }
            int payloadLength = readCount(input, MAX_PAYLOAD_SIZE, "payload length");
            byte[] expectedDigest = new byte[DIGEST_LENGTH];
            input.readFully(expectedDigest);
            if (payloadLength != input.available()) {
                throw new IOException("cache payload length does not match the file");
            }
            byte[] payload = new byte[payloadLength];
            input.readFully(payload);
            if (!MessageDigest.isEqual(expectedDigest, sha256().digest(payload))) {
                throw new IOException("cache checksum does not match");
            }
            return decodePayload(payload);
        } catch (EOFException e) {
            throw new IOException("truncated cache", e);
        }
    }

    private static Map<String, Entry> decodePayload(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            int entryCount = readCount(input, MAX_JARS, "JAR count");
            Map<String, Entry> entries = new LinkedHashMap<>();
            int totalConfigClasses = 0;
            int totalModIds = 0;
            for (int index = 0; index < entryCount; index++) {
                String path = readString(input);
                FileStamp stamp = new FileStamp(
                        input.readLong(),
                        input.readLong(),
                        input.readLong(),
                        readString(input));
                byte[] fingerprint = new byte[DIGEST_LENGTH];
                input.readFully(fingerprint);
                String metadataModId = readString(input);
                if (metadataModId.isEmpty()) metadataModId = null;
                int classCount = readCount(input, MAX_CONFIG_CLASSES_PER_JAR, "config class count");
                totalConfigClasses = checkedTotal(
                        totalConfigClasses,
                        classCount,
                        MAX_CONFIG_CLASSES_TOTAL,
                        "total config class count");
                List<String> configClasses = new ArrayList<>(classCount);
                for (int classIndex = 0; classIndex < classCount; classIndex++) {
                    configClasses.add(readString(input));
                }
                List<String> annotationModIds = readStrings(input, MAX_MOD_IDS_PER_JAR, "annotation mod ID count");
                totalModIds = checkedTotal(totalModIds, annotationModIds.size(), MAX_MOD_IDS_TOTAL, "total mod ID count");
                List<String> mappedModIds = readStrings(input, MAX_MOD_IDS_PER_JAR, "mapped mod ID count");
                totalModIds = checkedTotal(totalModIds, mappedModIds.size(), MAX_MOD_IDS_TOTAL, "total mod ID count");
                List<String> launcherModIds = readStrings(input, MAX_MOD_IDS_PER_JAR, "launcher mod ID count");
                totalModIds = checkedTotal(totalModIds, launcherModIds.size(), MAX_MOD_IDS_TOTAL, "total mod ID count");
                if (entries.put(path, new Entry(
                        stamp,
                        fingerprint,
                        immutable(configClasses),
                        metadataModId,
                        immutable(annotationModIds),
                        immutable(mappedModIds),
                        immutable(launcherModIds))) != null) {
                    throw new IOException("duplicate JAR path in cache");
                }
            }
            if (input.available() != 0) {
                throw new IOException("unexpected bytes after cache payload");
            }
            return entries;
        } catch (EOFException e) {
            throw new IOException("truncated cache payload", e);
        }
    }

    private static List<String> immutable(List<String> values) {
        return values.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static List<String> immutableSorted(Collection<String> values) {
        List<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return immutable(sorted);
    }

    private static int checkModIds(List<String> modIds, int total, String description) throws IOException {
        checkCount(modIds.size(), MAX_MOD_IDS_PER_JAR, description + " count");
        return checkedTotal(total, modIds.size(), MAX_MOD_IDS_TOTAL, "total mod ID count");
    }

    private static void writeStrings(DataOutputStream output, List<String> values) throws IOException {
        output.writeInt(values.size());
        for (String value : values) writeString(output, value);
    }

    private static List<String> readStrings(DataInputStream input, int maximum, String description) throws IOException {
        int count = readCount(input, maximum, description);
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) values.add(readString(input));
        return values;
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        checkCount(bytes.length, MAX_STRING_BYTES, "string length");
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = readCount(input, MAX_STRING_BYTES, "string length");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readCount(DataInputStream input, int maximum, String description) throws IOException {
        int value = input.readInt();
        checkCount(value, maximum, description);
        return value;
    }

    private static void checkCount(int value, int maximum, String description) throws IOException {
        if (value < 0 || value > maximum) {
            throw new IOException(description + " is outside the accepted range");
        }
    }

    private static int checkedTotal(int current, int added, int maximum, String description) throws IOException {
        if (added > maximum - current) {
            throw new IOException(description + " is outside the accepted range");
        }
        return current + added;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 is required by the Java runtime", e);
        }
    }

    private static boolean cacheEnabled() {
        String configured = System.getProperty(ENABLED_PROPERTY);
        if (configured == null || "true".equalsIgnoreCase(configured.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(configured.trim())) {
            return false;
        }
        Reference.LOGGER.warn(
                "Ignoring invalid {} value '{}'; expected true or false",
                ENABLED_PROPERTY,
                configured);
        return true;
    }

    static final class CachedData {
        private final List<String> configClasses;
        private final String metadataModId;
        private final List<String> annotationModIds;
        private final List<String> mappedModIds;
        private final List<String> launcherModIds;

        private CachedData(
                List<String> configClasses,
                String metadataModId,
                List<String> annotationModIds,
                List<String> mappedModIds,
                List<String> launcherModIds) {
            this.configClasses = configClasses;
            this.metadataModId = metadataModId;
            this.annotationModIds = annotationModIds;
            this.mappedModIds = mappedModIds;
            this.launcherModIds = launcherModIds;
        }

        List<String> configClasses() {
            return this.configClasses;
        }

        String metadataModId() {
            return this.metadataModId;
        }

        List<String> annotationModIds() {
            return this.annotationModIds;
        }

        List<String> mappedModIds() {
            return this.mappedModIds;
        }

        List<String> launcherModIds() {
            return this.launcherModIds;
        }
    }

    static final class FileStamp {
        private final long length;
        private final long lastModifiedNanos;
        private final long creationTimeNanos;
        private final String fileKey;

        private FileStamp(
                long length,
                long lastModifiedNanos,
                long creationTimeNanos,
                String fileKey) {
            this.length = length;
            this.lastModifiedNanos = lastModifiedNanos;
            this.creationTimeNanos = creationTimeNanos;
            this.fileKey = fileKey;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof FileStamp)) return false;
            FileStamp stamp = (FileStamp) other;
            return this.length == stamp.length
                    && this.lastModifiedNanos == stamp.lastModifiedNanos
                    && this.creationTimeNanos == stamp.creationTimeNanos
                    && this.fileKey.equals(stamp.fileKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.length, this.lastModifiedNanos, this.creationTimeNanos, this.fileKey);
        }
    }

    private static final class Entry {
        private final FileStamp stamp;
        private final byte[] fingerprint;
        private final List<String> configClasses;
        private final String metadataModId;
        private final List<String> annotationModIds;
        private final List<String> mappedModIds;
        private final List<String> launcherModIds;
        private final CachedData data;

        private Entry(
                FileStamp stamp,
                byte[] fingerprint,
                List<String> configClasses,
                String metadataModId,
                List<String> annotationModIds,
                List<String> mappedModIds,
                List<String> launcherModIds) {
            this.stamp = stamp;
            this.fingerprint = fingerprint;
            this.configClasses = configClasses;
            this.metadataModId = metadataModId;
            this.annotationModIds = annotationModIds;
            this.mappedModIds = mappedModIds;
            this.launcherModIds = launcherModIds;
            this.data = new CachedData(
                    configClasses, metadataModId, annotationModIds, mappedModIds, launcherModIds);
        }

        private Entry withStamp(FileStamp stamp) {
            if (this.stamp.equals(stamp)) return this;
            return new Entry(
                    stamp,
                    this.fingerprint,
                    this.configClasses,
                    this.metadataModId,
                    this.annotationModIds,
                    this.mappedModIds,
                    this.launcherModIds);
        }

        private CachedData data() {
            return this.data;
        }
    }

    private static final class BoundedByteArrayOutputStream extends ByteArrayOutputStream {
        private final int maximum;

        private BoundedByteArrayOutputStream(int maximum) {
            this.maximum = maximum;
        }

        @Override
        public synchronized void write(int value) {
            this.requireCapacity(1);
            super.write(value);
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            this.requireCapacity(length);
            super.write(bytes, offset, length);
        }

        private void requireCapacity(int added) {
            if (added < 0 || this.count > this.maximum - added) {
                throw new CacheSizeException();
            }
        }
    }

    private static final class CacheSizeException extends RuntimeException {
        private CacheSizeException() {
            super("cache payload exceeds the accepted size", null, false, false);
        }
    }
}
