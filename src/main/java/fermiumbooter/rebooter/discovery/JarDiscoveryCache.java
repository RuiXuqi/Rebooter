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
import java.util.jar.JarEntry;

final class JarDiscoveryCache {
    private static final String ENABLED_PROPERTY = "rebooter.discoveryCache";
    // RBTDISC Rebooter Discovery
    // Bump when format updated
    private static final long MAGIC = 0x5242544449534301L;
    private static final int DIGEST_LENGTH = 32;
    private static final int MAX_FILE_SIZE = 16 * 1024 * 1024;
    private static final int MAX_PAYLOAD_SIZE = MAX_FILE_SIZE - 4096;
    private static final int MAX_JARS = 4096;
    private static final int MAX_CONFIG_CLASSES_PER_JAR = 4096;
    private static final int MAX_CONFIG_CLASSES_TOTAL = 100_000;
    private static final int MAX_FALLBACK_MODS_PER_JAR = 4096;
    private static final int MAX_FALLBACK_MODS_TOTAL = 100_000;
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
     * Metadata-only identity keeps warm discovery from reading JAR contents. Mod files are assumed immutable
     * during launch; deployments that rewrite a file in place while restoring all metadata must disable the
     * discovery cache with {@code -Drebooter.discoveryCache=false}.
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

    CachedData lookup(File source, FileStamp stamp) {
        if (!this.enabled) return null;
        String path = source.getAbsolutePath();
        Entry entry = this.loaded.get(path);
        if (entry == null || !entry.stamp.equals(stamp)) return null;
        this.current.put(path, entry);
        return entry.data();
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
            Set<String> fallbackMods) {
        if (!this.enabled) return;
        String path = source.getAbsolutePath();
        List<String> sortedFallbackMods = new ArrayList<>(fallbackMods);
        Collections.sort(sortedFallbackMods);
        this.current.put(path, new Entry(
                stamp,
                fingerprint.clone(),
                immutable(configClasses),
                immutable(sortedFallbackMods)));
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

    static Fingerprint fingerprint() {
        return new Fingerprint();
    }

    private static byte[] encode(Map<String, Entry> entries, byte[] scanProfileDigest) throws IOException {
        checkCount(entries.size(), MAX_JARS, "JAR count");
        int totalConfigClasses = 0;
        int totalFallbackMods = 0;
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
            checkCount(
                    entry.fallbackMods.size(),
                    MAX_FALLBACK_MODS_PER_JAR,
                    "fallback mod count");
            totalFallbackMods = checkedTotal(
                    totalFallbackMods,
                    entry.fallbackMods.size(),
                    MAX_FALLBACK_MODS_TOTAL,
                    "total fallback mod count");
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
                List<String> configClasses = entry.configClasses;
                payload.writeInt(configClasses.size());
                for (String configClass : configClasses) {
                    writeString(payload, configClass);
                }
                payload.writeInt(entry.fallbackMods.size());
                for (String fallbackMod : entry.fallbackMods) {
                    writeString(payload, fallbackMod);
                }
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
            int totalFallbackMods = 0;
            for (int index = 0; index < entryCount; index++) {
                String path = readString(input);
                FileStamp stamp = new FileStamp(
                        input.readLong(),
                        input.readLong(),
                        input.readLong(),
                        readString(input));
                byte[] fingerprint = new byte[DIGEST_LENGTH];
                input.readFully(fingerprint);
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
                int fallbackModCount = readCount(input, MAX_FALLBACK_MODS_PER_JAR, "fallback mod count");
                totalFallbackMods = checkedTotal(
                        totalFallbackMods,
                        fallbackModCount,
                        MAX_FALLBACK_MODS_TOTAL,
                        "total fallback mod count");
                List<String> fallbackMods = new ArrayList<>(fallbackModCount);
                for (int fallbackIndex = 0; fallbackIndex < fallbackModCount; fallbackIndex++) {
                    fallbackMods.add(readString(input));
                }
                if (entries.put(path, new Entry(
                        stamp,
                        fingerprint,
                        immutable(configClasses),
                        immutable(fallbackMods))) != null) {
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

    static final class Fingerprint {
        private final MessageDigest digest = sha256();
        private final byte[] number = new byte[8];

        void add(JarEntry entry) {
            byte[] name = entry.getName().getBytes(StandardCharsets.UTF_8);
            this.updateLong(name.length);
            this.digest.update(name);
            this.updateLong(entry.getCrc());
            this.updateLong(entry.getCompressedSize());
            this.updateLong(entry.getSize());
            this.updateLong(entry.getMethod());
        }

        byte[] finish() {
            return this.digest.digest();
        }

        private void updateLong(long value) {
            for (int index = this.number.length - 1; index >= 0; index--) {
                this.number[index] = (byte) value;
                value >>>= 8;
            }
            this.digest.update(this.number);
        }
    }

    static final class CachedData {
        private final List<String> configClasses;
        private final List<String> fallbackMods;

        private CachedData(List<String> configClasses, List<String> fallbackMods) {
            this.configClasses = configClasses;
            this.fallbackMods = fallbackMods;
        }

        List<String> configClasses() {
            return this.configClasses;
        }

        List<String> fallbackMods() {
            return this.fallbackMods;
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
        private final List<String> fallbackMods;
        private final CachedData data;

        private Entry(
                FileStamp stamp,
                byte[] fingerprint,
                List<String> configClasses,
                List<String> fallbackMods) {
            this.stamp = stamp;
            this.fingerprint = fingerprint;
            this.configClasses = configClasses;
            this.fallbackMods = fallbackMods;
            this.data = new CachedData(configClasses, fallbackMods);
        }

        private Entry withStamp(FileStamp stamp) {
            if (this.stamp.equals(stamp)) return this;
            return new Entry(
                    stamp,
                    this.fingerprint,
                    this.configClasses,
                    this.fallbackMods);
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
