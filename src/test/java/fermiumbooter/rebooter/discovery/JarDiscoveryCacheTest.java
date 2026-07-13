package fermiumbooter.rebooter.discovery;

import fermiumbooter.ForgeTestEnvironment;
import fermiumbooter.annotations.MixinConfig;
import fermiumbooter.rebooter.Reference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class JarDiscoveryCacheTest {

    @TempDir
    Path gameDirectory;
    private String previousCacheProperty;

    @BeforeEach
    void clearCacheProperty() {
        this.previousCacheProperty = System.getProperty("rebooter.discoveryCache");
        System.clearProperty("rebooter.discoveryCache");
    }

    @AfterEach
    void restoreCacheProperty() {
        restoreProperty("rebooter.discoveryCache", this.previousCacheProperty);
    }

    @Test
    void warmCacheSkipsFullClassPrefilteringAndCarriesRebooterVersion() throws Exception {
        Path jar = createFixtureJar();
        prepareGameDirectory();

        JarDiscovery.registerConfigs();

        assertTrue(JarDiscovery.getPrefilterScanCount() > 0);
        assertTrue(JarDiscovery.getEnumeratedEntryCount() > 0);
        assertEquals(1, JarDiscovery.getFingerprintReadCount());
        Path cache = cacheFile();
        assertTrue(Files.isRegularFile(cache));
        assertTrue(contains(Files.readAllBytes(cache), Reference.VERSION.getBytes(StandardCharsets.UTF_8)));
        assertTrue(contains(
                Files.readAllBytes(cache),
                "cache-fixture".getBytes(StandardCharsets.UTF_8)));
        assertTrue(contains(
                Files.readAllBytes(cache),
                "mixins.cache-fixture.json".getBytes(StandardCharsets.UTF_8)));

        JarDiscovery.resetForTesting();
        JarDiscovery.registerConfigs();

        assertEquals(0, JarDiscovery.getPrefilterScanCount());
        assertEquals(0, JarDiscovery.getEnumeratedEntryCount());
        assertEquals(1, JarDiscovery.getFingerprintReadCount());
        assertTrue(Files.isRegularFile(jar));
    }

    @Test
    void warmCacheRestoresMultipleJarsAfterParallelFingerprinting() throws Exception {
        createFixtureJar();
        Path metadataJar = Files.createDirectories(this.gameDirectory.resolve("mods"))
                .resolve("parallel-metadata.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(metadataJar))) {
            output.putNextEntry(new JarEntry("mcmod.info"));
            output.write("[{\"modid\":\"parallel_cached_mod\"}]".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        prepareGameDirectory();
        JarDiscovery.registerConfigs();
        JarDiscovery.resetForTesting();

        assertTrue(JarDiscovery.isModPresent("parallel_cached_mod"));
        assertEquals(2, JarDiscovery.getFingerprintReadCount());
        assertEquals(0, JarDiscovery.getEnumeratedEntryCount());
        assertEquals(0, JarDiscovery.getPrefilterScanCount());
        assertEquals(0, JarDiscovery.getAsmClassReadCount());
    }

    @Test
    void disabledCacheSkipsContentFingerprintingDuringDiscovery() throws Exception {
        createFixtureJar();
        System.setProperty("rebooter.discoveryCache", "false");
        prepareGameDirectory();

        JarDiscovery.registerConfigs();

        assertTrue(JarDiscovery.getPrefilterScanCount() > 0);
        assertEquals(0, JarDiscovery.getFingerprintReadCount());
    }

    @Test
    void changedJarInvalidatesCachedClassMetadata() throws Exception {
        Path jar = createFixtureJar();
        prepareGameDirectory();
        JarDiscovery.registerConfigs();
        JarDiscovery.resetForTesting();

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addFixtureClass(output);
            output.putNextEntry(new JarEntry("changed/marker.txt"));
            output.write(1);
            output.closeEntry();
        }
        JarDiscovery.registerConfigs();

        assertTrue(JarDiscovery.getPrefilterScanCount() > 0);
    }

    @Test
    void corruptCacheFallsBackToClassScanning() throws Exception {
        createFixtureJar();
        prepareGameDirectory();
        JarDiscovery.registerConfigs();
        Path cache = cacheFile();
        byte[] bytes = Files.readAllBytes(cache);
        bytes[bytes.length - 1] ^= 0x5A;
        Files.write(cache, bytes);
        JarDiscovery.resetForTesting();

        JarDiscovery.registerConfigs();

        assertTrue(JarDiscovery.getPrefilterScanCount() > 0);
    }

    @Test
    void completeConfigResultRoundTripsThroughCache() throws Exception {
        Path source = Files.write(this.gameDirectory.resolve("source.jar"), new byte[]{1, 2, 3});
        byte[] fingerprint = JarDiscoveryCache.contentFingerprint(source.toFile());
        JarDiscoveryCache first = JarDiscoveryCache.load(this.gameDirectory.toFile(), "profile");
        first.record(
                source.toFile(),
                JarDiscoveryCache.stamp(source.toFile()),
                fingerprint,
                Collections.singletonList(cachedResult()),
                "metadata_mod",
                Collections.singleton("annotation_mod"),
                Collections.singleton("mapped_mod"),
                Collections.singleton("launcher_mod"));
        first.save();

        JarDiscoveryCache reloaded = JarDiscoveryCache.load(this.gameDirectory.toFile(), "profile");
        JarDiscoveryCache.CachedData cached = reloaded.lookup(
                source.toFile(), JarDiscoveryCache.stamp(source.toFile()), fingerprint);

        assertNotNull(cached);
        assertEquals("metadata_mod", cached.metadataModId());
        assertEquals(Collections.singletonList("annotation_mod"), cached.annotationModIds());
        assertEquals(Collections.singletonList("mapped_mod"), cached.mappedModIds());
        assertEquals(Collections.singletonList("launcher_mod"), cached.launcherModIds());
        assertCachedResult(cached.configResults().get(0));
    }

    @Test
    void reusableFingerprinterResetsDigestBetweenFiles() throws Exception {
        byte[] firstBytes = new byte[]{1, 2, 3};
        byte[] secondBytes = new byte[]{4, 5, 6, 7};
        Path first = Files.write(this.gameDirectory.resolve("first.jar"), firstBytes);
        Path second = Files.write(this.gameDirectory.resolve("second.jar"), secondBytes);
        JarDiscoveryCache.ContentFingerprinter fingerprinter = new JarDiscoveryCache.ContentFingerprinter();

        assertArrayEquals(
                MessageDigest.getInstance("SHA-256").digest(firstBytes),
                fingerprinter.fingerprint(first.toFile()));
        assertArrayEquals(
                MessageDigest.getInstance("SHA-256").digest(secondBytes),
                fingerprinter.fingerprint(second.toFile()));
        assertArrayEquals(
                MessageDigest.getInstance("SHA-256").digest(firstBytes),
                fingerprinter.fingerprint(first.toFile()));
    }

    @Test
    void parallelFingerprintFailureDoesNotDiscardOtherResults() throws Exception {
        Path broken = Files.write(this.gameDirectory.resolve("broken.jar"), new byte[]{1});
        Path healthy = Files.write(this.gameDirectory.resolve("healthy.jar"), new byte[]{2, 3, 4});
        JarDiscoveryCache cache = JarDiscoveryCache.load(this.gameDirectory.toFile(), "profile");
        byte[] fingerprint = new byte[32];
        cache.record(
                broken.toFile(),
                JarDiscoveryCache.stamp(broken.toFile()),
                fingerprint,
                Collections.emptyList(),
                null,
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet());
        cache.record(
                healthy.toFile(),
                JarDiscoveryCache.stamp(healthy.toFile()),
                fingerprint,
                Collections.emptyList(),
                null,
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet());
        cache.save();
        cache = JarDiscoveryCache.load(this.gameDirectory.toFile(), "profile");
        Files.delete(broken);
        Files.createDirectory(broken);

        FingerprintCollector.Batch batch = FingerprintCollector.collect(
                Arrays.asList(broken.toFile(), healthy.toFile()),
                cache,
                new JarDiscoveryCache.ContentFingerprinter());

        assertNotNull(batch.result(broken.toFile()).failure());
        assertNull(batch.result(healthy.toFile()).failure());
        assertArrayEquals(
                MessageDigest.getInstance("SHA-256").digest(new byte[]{2, 3, 4}),
                batch.result(healthy.toFile()).fingerprint());
        assertEquals(2, batch.fingerprintCount());
        assertEquals(3, batch.fingerprintBytes());
    }

    @Test
    void missingNestedConfigDataWithValidChecksumRejectsTheWholeCache() throws Exception {
        Path source = Files.write(this.gameDirectory.resolve("source.jar"), new byte[]{1, 2, 3});
        byte[] fingerprint = JarDiscoveryCache.contentFingerprint(source.toFile());
        JarDiscoveryCache first = JarDiscoveryCache.load(this.gameDirectory.toFile(), "profile");
        first.record(
                source.toFile(),
                JarDiscoveryCache.stamp(source.toFile()),
                fingerprint,
                Collections.singletonList(cachedResult()),
                "must_not_restore",
                Collections.singleton("must_not_restore"),
                Collections.singleton("must_not_restore"),
                Collections.singleton("must_not_restore"));
        first.save();
        removeToggleDataAndRefreshChecksum(cacheFile());

        JarDiscoveryCache reloaded = JarDiscoveryCache.load(this.gameDirectory.toFile(), "profile");

        assertNull(reloaded.lookup(
                source.toFile(), JarDiscoveryCache.stamp(source.toFile()), fingerprint));
    }

    @Test
    void invalidUtf8WithValidChecksumRejectsTheWholeCache() throws Exception {
        Path source = Files.write(this.gameDirectory.resolve("source.jar"), new byte[]{1, 2, 3});
        byte[] fingerprint = JarDiscoveryCache.contentFingerprint(source.toFile());
        JarDiscoveryCache first = JarDiscoveryCache.load(this.gameDirectory.toFile(), "profile");
        first.record(
                source.toFile(),
                JarDiscoveryCache.stamp(source.toFile()),
                fingerprint,
                Collections.singletonList(cachedResult()),
                "must_not_restore",
                Collections.singleton("must_not_restore"),
                Collections.singleton("must_not_restore"),
                Collections.singleton("must_not_restore"));
        first.save();
        corruptConfigNameUtf8AndRefreshChecksum(cacheFile());

        JarDiscoveryCache reloaded = JarDiscoveryCache.load(this.gameDirectory.toFile(), "profile");

        assertNull(reloaded.lookup(
                source.toFile(), JarDiscoveryCache.stamp(source.toFile()), fingerprint));
    }

    @Test
    void cacheFromAnotherRebooterVersionIsRejected() throws Exception {
        createFixtureJar();
        prepareGameDirectory();
        JarDiscovery.registerConfigs();
        Path cache = cacheFile();
        byte[] bytes = Files.readAllBytes(cache);
        byte[] version = Reference.VERSION.getBytes(StandardCharsets.UTF_8);
        int versionOffset = indexOf(bytes, version);
        assertTrue(versionOffset >= 0);
        bytes[versionOffset] ^= 0x01;
        Files.write(cache, bytes);
        JarDiscovery.resetForTesting();

        JarDiscovery.registerConfigs();

        assertTrue(JarDiscovery.getPrefilterScanCount() > 0);
        assertTrue(JarDiscovery.getEnumeratedEntryCount() > 0);
    }

    @Test
    void timestampChangeUsesStrongFingerprintAndRefreshesTheFastPath() throws Exception {
        Path jar = createFixtureJar();
        prepareGameDirectory();
        JarDiscovery.registerConfigs();
        JarDiscovery.resetForTesting();
        Files.setLastModifiedTime(jar, FileTime.fromMillis(Files.getLastModifiedTime(jar).toMillis() + 2_000));

        JarDiscovery.registerConfigs();

        assertEquals(0, JarDiscovery.getEnumeratedEntryCount());
        assertEquals(0, JarDiscovery.getPrefilterScanCount());

        JarDiscovery.resetForTesting();
        JarDiscovery.registerConfigs();

        assertEquals(0, JarDiscovery.getEnumeratedEntryCount());
        assertEquals(0, JarDiscovery.getPrefilterScanCount());
    }

    @Test
    void sameSizeAndTimestampAtomicReplacementMissesTheFastPath() throws Exception {
        Path source = this.gameDirectory.resolve("source.jar");
        Files.write(source, new byte[]{1, 2, 3});
        FileTime originalModifiedTime = Files.getLastModifiedTime(source);
        JarDiscoveryCache.FileStamp originalStamp = JarDiscoveryCache.stamp(source.toFile());
        byte[] fingerprint = JarDiscoveryCache.contentFingerprint(source.toFile());
        JarDiscoveryCache first = JarDiscoveryCache.load(this.gameDirectory.toFile(), "profile");
        first.record(
                source.toFile(),
                originalStamp,
                fingerprint,
                Collections.emptyList(),
                null,
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet());
        first.save();
        Path replacement = this.gameDirectory.resolve("replacement.jar");
        Files.write(replacement, new byte[]{4, 5, 6});
        Files.setLastModifiedTime(replacement, originalModifiedTime);
        Files.move(
                replacement,
                source,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);

        JarDiscoveryCache reloaded = JarDiscoveryCache.load(this.gameDirectory.toFile(), "profile");

        assertNull(reloaded.lookup(
                source.toFile(),
                JarDiscoveryCache.stamp(source.toFile()),
                JarDiscoveryCache.contentFingerprint(source.toFile())));
    }

    @Test
    void inPlaceContentChangeMissesTheFastPathWhenFileStateIsRestored() throws Exception {
        Path source = this.gameDirectory.resolve("source.jar");
        Files.write(source, new byte[]{1, 2, 3});
        FileTime originalModifiedTime = Files.getLastModifiedTime(source);
        JarDiscoveryCache first = JarDiscoveryCache.load(this.gameDirectory.toFile(), "profile");
        first.record(
                source.toFile(),
                JarDiscoveryCache.stamp(source.toFile()),
                JarDiscoveryCache.contentFingerprint(source.toFile()),
                Collections.emptyList(),
                "old_mod",
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet());
        first.save();

        Files.write(source, new byte[]{4, 5, 6});
        Files.setLastModifiedTime(source, originalModifiedTime);
        JarDiscoveryCache reloaded = JarDiscoveryCache.load(this.gameDirectory.toFile(), "profile");

        assertNull(reloaded.lookup(
                source.toFile(),
                JarDiscoveryCache.stamp(source.toFile()),
                JarDiscoveryCache.contentFingerprint(source.toFile())));
    }

    @Test
    void changedScanProfileInvalidatesCachedClassMetadata() throws Exception {
        Path source = Files.createFile(this.gameDirectory.resolve("source.jar"));
        byte[] fingerprint = new byte[32];
        JarDiscoveryCache first = JarDiscoveryCache.load(this.gameDirectory.toFile(), "profile-a");
        first.record(
                source.toFile(),
                JarDiscoveryCache.stamp(source.toFile()),
                fingerprint,
                Collections.singletonList(cachedResult()),
                null,
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet());
        first.save();

        JarDiscoveryCache changed = JarDiscoveryCache.load(this.gameDirectory.toFile(), "profile-b");

        assertNull(changed.lookup(source.toFile(), JarDiscoveryCache.stamp(source.toFile()), fingerprint));
    }

    @Test
    void largeScanProfileUsesAFixedSizeCacheIdentity() throws Exception {
        char[] profileCharacters = new char[70_000];
        Arrays.fill(profileCharacters, 'a');
        String profile = new String(profileCharacters);
        Path source = Files.createFile(this.gameDirectory.resolve("source.jar"));
        byte[] fingerprint = new byte[32];
        JarDiscoveryCache first = JarDiscoveryCache.load(this.gameDirectory.toFile(), profile);
        first.record(
                source.toFile(),
                JarDiscoveryCache.stamp(source.toFile()),
                fingerprint,
                Collections.singletonList(cachedResult()),
                null,
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet());
        first.save();

        JarDiscoveryCache reloaded = JarDiscoveryCache.load(this.gameDirectory.toFile(), profile);

        assertNotNull(reloaded.lookup(
                source.toFile(), JarDiscoveryCache.stamp(source.toFile()), fingerprint));
    }

    @Test
    void absentOrdinaryModWaitsForCompleteJarIndexing() throws Exception {
        createFixtureJar();
        prepareGameDirectory();

        assertEquals(false, JarDiscovery.isModPresent("ordinary_optional_mod"));

        assertTrue(JarDiscovery.getPrefilterScanCount() > 0);
        assertTrue(Files.exists(cacheFile()));
    }

    @Test
    void disabledCacheDoesNotReadOrWriteCache() throws Exception {
        Path jar = createFixtureJar();
        byte[] fingerprint = new byte[32];
        byte[] existingCache = new byte[]{1, 2, 3};
        Files.createDirectories(cacheFile().getParent());
        Files.write(cacheFile(), existingCache);
        System.setProperty("rebooter.discoveryCache", "false");
        JarDiscoveryCache cache = JarDiscoveryCache.load(this.gameDirectory.toFile());
        JarDiscoveryCache.FileStamp stamp = JarDiscoveryCache.stamp(jar.toFile());
        assertEquals(null, cache.lookup(jar.toFile(), stamp, fingerprint));
        cache.record(
                jar.toFile(),
                stamp,
                fingerprint,
                Collections.emptyList(),
                null,
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet());
        cache.save();

        assertArrayEquals(existingCache, Files.readAllBytes(cacheFile()));
    }

    @Test
    void explicitTrueAndInvalidValuesKeepCacheEnabled() throws Exception {
        assertCacheEnabled("true");
        Files.delete(cacheFile());
        assertCacheEnabled("invalid");
    }

    private void prepareGameDirectory() throws Exception {
        System.setProperty("rebooter.gameDir", this.gameDirectory.toString());
        ForgeTestEnvironment.clearInjectedGameDirectory();
        JarDiscovery.resetForTesting();
    }

    private Path createFixtureJar() throws Exception {
        Path jar = Files.createDirectories(this.gameDirectory.resolve("mods")).resolve("cache-fixture.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addFixtureClass(output);
        }
        return jar;
    }

    private static void addFixtureClass(JarOutputStream output) throws Exception {
        addClass(output, JarDiscoveryCacheTest.class);
        addClass(output, CachedConfig.class);
    }

    private static void addClass(JarOutputStream output, Class<?> type) throws Exception {
        String resource = classResource(type);
        try (InputStream input = Objects.requireNonNull(
                type.getClassLoader().getResourceAsStream(resource), resource)) {
            output.putNextEntry(new JarEntry(resource));
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            output.closeEntry();
        }
    }

    private static String classResource(Class<?> type) {
        return type.getName().replace('.', '/') + ".class";
    }

    private static ConfigReader.Result cachedResult() {
        return new ConfigReader.Result(
                "cached-config",
                Collections.singletonList(new ConfigReader.Toggle(
                        "Enabled Field",
                        "mixins.cached.early.json",
                        "mixins.cached.late.json",
                        true,
                        Collections.singletonList(new CompatRule(
                                "cached_mod",
                                false,
                                false,
                                true,
                                "cached reason")))));
    }

    private static void assertCachedResult(ConfigReader.Result result) {
        assertEquals("cached-config", result.configName());
        assertEquals(1, result.toggles().size());
        ConfigReader.Toggle toggle = result.toggles().get(0);
        assertEquals("Enabled Field", toggle.configFieldName());
        assertEquals("mixins.cached.early.json", toggle.earlyMixinName());
        assertEquals("mixins.cached.late.json", toggle.lateMixinName());
        assertTrue(toggle.defaultValue());
        assertEquals(1, toggle.compatibilityRules().size());
        CompatRule rule = toggle.compatibilityRules().get(0);
        assertEquals("cached_mod", rule.modid());
        assertFalse(rule.desired());
        assertFalse(rule.disableMixin());
        assertTrue(rule.warnIngame());
        assertEquals("cached reason", rule.reason());
    }

    private static void removeToggleDataAndRefreshChecksum(Path cache) throws Exception {
        byte[] bytes = Files.readAllBytes(cache);
        ByteBuffer header = ByteBuffer.wrap(bytes);
        header.getLong();
        int versionLength = header.getInt();
        header.position(header.position() + versionLength + 32);
        int payloadLengthOffset = header.position();
        int payloadLength = header.getInt();
        byte[] configName = "cached-config".getBytes(StandardCharsets.UTF_8);
        int configNameOffset = indexOf(bytes, configName);
        assertTrue(configNameOffset >= header.position() + 32);
        int toggleCountOffset = configNameOffset + configName.length;
        ByteBuffer payload = ByteBuffer.wrap(bytes);
        payload.position(toggleCountOffset);
        assertEquals(1, payload.getInt());
        int toggleDataOffset = payload.position();
        skipString(payload);
        skipString(payload);
        skipString(payload);
        payload.get();
        int ruleCount = payload.getInt();
        for (int index = 0; index < ruleCount; index++) {
            skipString(payload);
            payload.position(payload.position() + 3);
            skipString(payload);
        }
        int toggleDataEnd = payload.position();
        int removedBytes = toggleDataEnd - toggleDataOffset;
        byte[] shortened = new byte[bytes.length - removedBytes];
        System.arraycopy(bytes, 0, shortened, 0, toggleDataOffset);
        System.arraycopy(
                bytes,
                toggleDataEnd,
                shortened,
                toggleDataOffset,
                bytes.length - toggleDataEnd);
        ByteBuffer.wrap(shortened).putInt(toggleCountOffset, 0);
        ByteBuffer.wrap(shortened).putInt(payloadLengthOffset, payloadLength - removedBytes);
        refreshChecksum(shortened);
        Files.write(cache, shortened);
    }

    private static void corruptConfigNameUtf8AndRefreshChecksum(Path cache) throws Exception {
        byte[] bytes = Files.readAllBytes(cache);
        int configNameOffset = indexOf(bytes, "cached-config".getBytes(StandardCharsets.UTF_8));
        assertTrue(configNameOffset >= 0);
        bytes[configNameOffset] = (byte) 0xC0;
        refreshChecksum(bytes);
        Files.write(cache, bytes);
    }

    private static void refreshChecksum(byte[] bytes) throws Exception {
        ByteBuffer header = ByteBuffer.wrap(bytes);
        header.getLong();
        int versionLength = header.getInt();
        header.position(header.position() + versionLength + 32);
        int payloadLength = header.getInt();
        int checksumOffset = header.position();
        int payloadOffset = checksumOffset + 32;
        byte[] checksum = MessageDigest.getInstance("SHA-256")
                .digest(Arrays.copyOfRange(bytes, payloadOffset, payloadOffset + payloadLength));
        System.arraycopy(checksum, 0, bytes, checksumOffset, checksum.length);
    }

    private static void skipString(ByteBuffer buffer) {
        int length = buffer.getInt();
        buffer.position(buffer.position() + length);
    }

    private Path cacheFile() {
        return this.gameDirectory.resolve("cache/rebooter/jar-discovery.bin");
    }

    private void assertCacheEnabled(String propertyValue) throws Exception {
        System.setProperty("rebooter.discoveryCache", propertyValue);
        byte[] original = new byte[]{1, 2, 3};
        Files.createDirectories(cacheFile().getParent());
        Files.write(cacheFile(), original);
        JarDiscoveryCache cache = JarDiscoveryCache.load(this.gameDirectory.toFile());
        Path sourcePath = this.gameDirectory.resolve("source.jar");
        if (!Files.exists(sourcePath)) Files.createFile(sourcePath);
        File source = sourcePath.toFile();
        cache.record(
                source,
                JarDiscoveryCache.stamp(source),
                new byte[32],
                Collections.emptyList(),
                null,
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet());
        cache.save();
        assertNotEquals(original.length, Files.size(cacheFile()));
    }

    private static boolean contains(byte[] bytes, byte[] expected) {
        return indexOf(bytes, expected) >= 0;
    }

    private static int indexOf(byte[] bytes, byte[] expected) {
        outer:
        for (int offset = 0; offset <= bytes.length - expected.length; offset++) {
            for (int index = 0; index < expected.length; index++) {
                if (bytes[offset + index] != expected[index]) {
                    continue outer;
                }
            }
            return offset;
        }
        return -1;
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    @MixinConfig(name = "cache-fixture")
    static class CachedConfig {

        @MixinConfig.MixinToggle(earlyMixin = "mixins.cache-fixture.json", defaultValue = true)
        static boolean enabled;
    }

}
