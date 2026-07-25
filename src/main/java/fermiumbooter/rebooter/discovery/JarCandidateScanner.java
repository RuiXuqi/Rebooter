package fermiumbooter.rebooter.discovery;

import fermiumbooter.rebooter.Reference;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

final class JarCandidateScanner {
    private static final int LAUNCHER_DISCOVERY_VERSION = 1;
    private static final String OPTIFINE_TWEAKER = "optifine.OptiFineForgeTweaker";

    private final Map<String, Set<String>> packageMappings;
    private final Set<String> classScanAllowlist;
    private final JarDiscoveryCache cache;
    private final FingerprintCollector.Batch preparedFingerprints;
    private final JarDiscoveryCache.ContentFingerprinter fingerprinter;
    private final DiscoveryStatistics statistics;
    private final ClassMetadataScanner classScanner;

    JarCandidateScanner(
            Map<String, Set<String>> packageMappings,
            Set<String> classScanAllowlist,
            JarDiscoveryCache cache,
            FingerprintCollector.Batch preparedFingerprints,
            JarDiscoveryCache.ContentFingerprinter fingerprinter,
            DiscoveryStatistics statistics) {
        this.packageMappings = packageMappings;
        this.classScanAllowlist = classScanAllowlist;
        this.cache = cache;
        this.preparedFingerprints = preparedFingerprints;
        this.fingerprinter = fingerprinter;
        this.statistics = statistics;
        this.classScanner = new ClassMetadataScanner(statistics);
    }

    static String cacheProfile() {
        return "launcher-discovery-v" + LAUNCHER_DISCOVERY_VERSION + '\n'
                + "manifest:TweakClass=" + OPTIFINE_TWEAKER + "=>optifine\n";
    }

    JarScanResult scan(File file) {
        PreparedCandidate prepared = this.prepare(file);
        if (prepared == null) {
            return JarScanResult.ignored();
        }
        if (prepared.cached != null) {
            return this.restoreCached(file, prepared);
        }
        this.statistics.cacheMiss();
        return this.scanFresh(file, prepared);
    }

    private PreparedCandidate prepare(File file) {
        FingerprintCollector.Result preparedFingerprint = this.preparedFingerprints.result(file);
        if (preparedFingerprint != null) {
            if (preparedFingerprint.failure() != null) {
                Reference.LOGGER.error(
                        "Failed to inspect discovery candidate {}",
                        file,
                        preparedFingerprint.failure());
                return null;
            }
            JarDiscoveryCache.FileStamp stamp = preparedFingerprint.stamp();
            byte[] fingerprint = preparedFingerprint.fingerprint();
            return new PreparedCandidate(
                    stamp,
                    fingerprint,
                    this.cache.lookup(file, stamp, fingerprint));
        }
        try {
            return new PreparedCandidate(JarDiscoveryCache.stamp(file), null, null);
        } catch (IOException e) {
            Reference.LOGGER.error("Failed to inspect discovery candidate {}", file, e);
            return null;
        }
    }

    private JarScanResult restoreCached(File file, PreparedCandidate prepared) {
        this.statistics.cacheHit();
        try {
            if (prepared.initialStamp.equals(JarDiscoveryCache.stamp(file))) {
                return JarScanResult.cached(prepared.cached);
            }
        } catch (IOException e) {
            Reference.LOGGER.error("Failed to verify cached discovery candidate {}", file, e);
        }
        Reference.LOGGER.warn("Cached discovery candidate changed while being read; ignoring {}", file);
        return JarScanResult.ignored();
    }

    private JarScanResult scanFresh(File file, PreparedCandidate prepared) {
        try (JarFile jar = new JarFile(file, false)) {
            MutableJarMetadata metadata = new MutableJarMetadata();
            boolean optiFine = this.readLauncherMetadata(jar, file, metadata);
            metadata.metadataModId = this.readMetadataModId(jar, file);
            this.scanEntries(jar, file, optiFine, metadata);

            JarDiscoveryCache.FileStamp finalStamp = JarDiscoveryCache.stamp(file);
            if (!prepared.initialStamp.equals(finalStamp)) {
                Reference.LOGGER.warn("Discovery candidate changed while reading classes; ignoring {}", file);
                return JarScanResult.ignored();
            }
            byte[] fingerprint = prepared.fingerprint;
            if (this.cache.isEnabled() && fingerprint == null) {
                fingerprint = this.contentFingerprint(file);
                if (!finalStamp.equals(JarDiscoveryCache.stamp(file))) {
                    Reference.LOGGER.warn("Discovery candidate changed while fingerprinting; ignoring {}", file);
                    return JarScanResult.ignored();
                }
            }
            if (this.cache.isEnabled()) {
                this.cache.record(
                        file,
                        finalStamp,
                        fingerprint,
                        metadata.configResults,
                        metadata.metadataModId,
                        metadata.annotationModIds,
                        metadata.mappedModIds,
                        metadata.launcherModIds);
            }
            return metadata.result();
        } catch (IOException e) {
            Reference.LOGGER.error("Failed to scan discovery metadata in {}", file, e);
            return JarScanResult.ignored();
        }
    }

    private boolean readLauncherMetadata(JarFile jar, File file, MutableJarMetadata metadata) {
        try {
            Manifest manifest = jar.getManifest();
            if (manifest != null
                    && OPTIFINE_TWEAKER.equals(manifest.getMainAttributes().getValue("TweakClass"))) {
                metadata.launcherModIds.add("optifine");
                return true;
            }
        } catch (IOException e) {
            Reference.LOGGER.debug("Skipping unreadable manifest in {}", file, e);
        }
        return false;
    }

    private String readMetadataModId(JarFile jar, File file) {
        long metadataStarted = this.statistics.start();
        JarEntry mcmodInfo = jar.getJarEntry("mcmod.info");
        try {
            if (mcmodInfo == null) {
                return null;
            }
            try (InputStream input = jar.getInputStream(mcmodInfo)) {
                return JsonInfoReader.firstModId(input);
            } catch (IOException e) {
                Reference.LOGGER.debug("Skipping unreadable mcmod.info in {}", file, e);
                return null;
            }
        } finally {
            this.statistics.metadata(this.statistics.elapsed(metadataStarted), mcmodInfo != null);
        }
    }

    private void scanEntries(
            JarFile jar,
            File file,
            boolean optiFine,
            MutableJarMetadata metadata) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            long entryStarted = this.statistics.start();
            JarEntry entry = entries.nextElement();
            this.collectMappedModIds(entry.getName(), metadata.mappedModIds);
            this.statistics.entryEnumeration(this.statistics.elapsed(entryStarted));

            long classStarted = this.statistics.start();
            if (optiFine || !DiscoveryClassFilter.isScannable(entry, this.classScanAllowlist)) {
                this.statistics.classFilter(this.statistics.elapsed(classStarted));
                continue;
            }
            boolean classFilterTimingRecorded = false;
            try (InputStream input = jar.getInputStream(entry)) {
                this.statistics.classFilter(this.statistics.elapsed(classStarted));
                classFilterTimingRecorded = true;
                ClassScanResult result = this.classScanner.scan(input, entry.getSize());
                if (!result.hasMetadata()) {
                    continue;
                }
                if (result.configResult() != null) {
                    metadata.configResults.add(result.configResult());
                }
                if (result.modId() != null) {
                    metadata.annotationModIds.add(result.modId());
                }
            } catch (IOException | RuntimeException e) {
                if (!classFilterTimingRecorded) {
                    this.statistics.classFilter(this.statistics.elapsed(classStarted));
                }
                Reference.LOGGER.debug("Skipping unreadable class '{}' in {}", entry.getName(), file, e);
            }
        }
    }

    private void collectMappedModIds(String entryName, Set<String> destination) {
        for (Map.Entry<String, Set<String>> mapping : this.packageMappings.entrySet()) {
            if (entryName.startsWith(mapping.getKey())) {
                destination.addAll(mapping.getValue());
            }
        }
    }

    private byte[] contentFingerprint(File file) throws IOException {
        long started = this.statistics.start();
        try {
            return this.fingerprinter.fingerprint(file);
        } finally {
            this.statistics.fingerprint(
                    this.fingerprinter.bytesRead(),
                    this.statistics.elapsed(started));
        }
    }

    private static final class PreparedCandidate {
        private final JarDiscoveryCache.FileStamp initialStamp;
        private final byte[] fingerprint;
        private final JarDiscoveryCache.CachedData cached;

        private PreparedCandidate(
                JarDiscoveryCache.FileStamp initialStamp,
                byte[] fingerprint,
                JarDiscoveryCache.CachedData cached) {
            this.initialStamp = initialStamp;
            this.fingerprint = fingerprint;
            this.cached = cached;
        }
    }

    private static final class MutableJarMetadata {
        private final Set<String> mappedModIds = new HashSet<>();
        private final Set<String> annotationModIds = new HashSet<>();
        private final Set<String> launcherModIds = new HashSet<>();
        private final List<DiscoveredConfig> configResults = new ArrayList<>();
        private String metadataModId;

        private JarScanResult result() {
            return JarScanResult.scanned(
                    this.mappedModIds,
                    this.metadataModId,
                    this.annotationModIds,
                    this.launcherModIds,
                    this.configResults);
        }
    }
}
