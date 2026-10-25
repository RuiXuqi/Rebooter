package fermiumbooter.rebooter.discovery;

import fermiumbooter.rebooter.Reference;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

final class JarCandidateScanner {
    private static final int LAUNCHER_DISCOVERY_VERSION = 1;
    private static final String OPTIFINE_TWEAKER = "optifine.OptiFineForgeTweaker";
    private static final int WORKER_COUNT = 4;
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();

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

    static List<JarScanResult> parallelScan(
            List<File> candidates,
            Map<String, Set<String>> packageMappings,
            Set<String> classScanAllowlist,
            JarDiscoveryCache cache,
            FingerprintCollector.Batch preparedFingerprints,
            DiscoveryStatistics statistics) {
        if (candidates.size() <= 1) {
            List<JarScanResult> results = new ArrayList<>(candidates.size());
            JarCandidateScanner scanner = new JarCandidateScanner(
                    packageMappings,
                    classScanAllowlist,
                    cache,
                    preparedFingerprints,
                    new JarDiscoveryCache.ContentFingerprinter(),
                    statistics);
            for (File candidate : candidates) results.add(scanner.scan(candidate));
            return results;
        }
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(
                WORKER_COUNT,
                JarCandidateScanner::newWorkerThread);
        try {
            if (executor.prestartAllCoreThreads() != WORKER_COUNT) {
                throw new IllegalStateException("Unable to start all candidate scan workers");
            }
        } catch (RuntimeException e) {
            executor.shutdownNow();
            Reference.LOGGER.warn("Unable to start parallel candidate scan workers; using one worker", e);
            List<JarScanResult> results = new ArrayList<>(candidates.size());
            JarCandidateScanner scanner = new JarCandidateScanner(
                    packageMappings,
                    classScanAllowlist,
                    cache,
                    preparedFingerprints,
                    new JarDiscoveryCache.ContentFingerprinter(),
                    statistics);
            for (File candidate : candidates) results.add(scanner.scan(candidate));
            return results;
        }
        try {
            AtomicInteger nextCandidate = new AtomicInteger();
            List<Future<WorkerResult>> workers = new ArrayList<>(WORKER_COUNT);
            for (int workerIndex = 0; workerIndex < WORKER_COUNT; workerIndex++) {
                DiscoveryStatistics workerStatistics = statistics.newWorkerStatistics();
                workers.add(executor.submit(() -> scanCandidates(
                        candidates,
                        nextCandidate,
                        new JarCandidateScanner(
                                packageMappings,
                                classScanAllowlist,
                                cache,
                                preparedFingerprints,
                                new JarDiscoveryCache.ContentFingerprinter(),
                                workerStatistics))));
            }
            List<JarScanResult> results = new ArrayList<>(Collections.nCopies(candidates.size(), null));
            for (Future<WorkerResult> worker : workers) {
                WorkerResult completed = worker.get();
                statistics.merge(completed.statistics);
                for (IndexedResult scanned : completed.scanned) {
                    results.set(scanned.index, scanned.result);
                }
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while scanning discovery candidates", e);
        } catch (ExecutionException e) {
            throw propagate(e.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private static WorkerResult scanCandidates(
            List<File> candidates,
            AtomicInteger nextCandidate,
            JarCandidateScanner scanner) {
        List<IndexedResult> scanned = new ArrayList<>();
        int index;
        while ((index = nextCandidate.getAndIncrement()) < candidates.size()) {
            scanned.add(new IndexedResult(index, scanner.scan(candidates.get(index))));
        }
        return new WorkerResult(scanned, scanner.statistics);
    }

    private static Thread newWorkerThread(Runnable task) {
        Thread thread = new Thread(
                task,
                "Rebooter Candidate Scan " + THREAD_NUMBER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

    private static RuntimeException propagate(Throwable cause) {
        if (cause instanceof RuntimeException) return (RuntimeException) cause;
        if (cause instanceof Error) throw (Error) cause;
        return new IllegalStateException("Unexpected candidate scan worker failure", cause);
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
                    this.cache.lookup(file, stamp, fingerprint, preparedFingerprint.stampMatched()));
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
        boolean fingerprintWhileScanning = this.cache.isEnabled() && prepared.fingerprint == null;
        try (InputStream fileInput = openFileInput(file, fingerprintWhileScanning);
             JarFile jar = new JarFile(file, false)) {
            MutableJarMetadata metadata = new MutableJarMetadata();
            boolean optiFine = this.readLauncherMetadata(jar, file, metadata);
            metadata.metadataIdentity = this.readMetadataIdentity(jar, file);
            this.scanEntries(jar, file, optiFine, metadata);

            JarDiscoveryCache.FileStamp finalStamp = JarDiscoveryCache.stamp(file);
            if (!prepared.initialStamp.equals(finalStamp)) {
                Reference.LOGGER.warn("Discovery candidate changed while reading classes; ignoring {}", file);
                return JarScanResult.ignored();
            }
            byte[] fingerprint = prepared.fingerprint;
            if (fingerprintWhileScanning) {
                fingerprint = this.contentFingerprint(fileInput);
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
                        metadata.metadataIdentity,
                        metadata.annotationIdentities.values(),
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

    private ModIdentity readMetadataIdentity(JarFile jar, File file) {
        long metadataStarted = this.statistics.start();
        JarEntry mcmodInfo = jar.getJarEntry("mcmod.info");
        try {
            if (mcmodInfo == null) {
                return null;
            }
            try (InputStream input = jar.getInputStream(mcmodInfo)) {
                return JsonInfoReader.firstMod(input);
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
        if (optiFine) {
            return;
        }
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            long entryStarted = this.statistics.start();
            JarEntry entry = entries.nextElement();
            if (!this.packageMappings.isEmpty() && !entry.isDirectory()) {
                this.collectMappedModIds(entry.getName(), metadata.mappedModIds);
            }
            this.statistics.entryEnumeration(this.statistics.elapsed(entryStarted));

            long classStarted = this.statistics.start();
            if (!DiscoveryClassFilter.isScannable(entry, this.classScanAllowlist)) {
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
                if (result.modIdentity() != null) {
                    metadata.addAnnotationIdentity(result.modIdentity());
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

    private static InputStream openFileInput(File file, boolean fingerprintWhileScanning) throws IOException {
        return fingerprintWhileScanning
                ? new BufferedInputStream(Files.newInputStream(file.toPath()))
                : null;
    }

    private byte[] contentFingerprint(InputStream fileInput) throws IOException {
        long started = this.statistics.start();
        try {
            return this.fingerprinter.fingerprint(fileInput);
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
        private final Map<String, ModIdentity> annotationIdentities = new LinkedHashMap<>();
        private final Set<String> launcherModIds = new HashSet<>();
        private final List<DiscoveredConfig> configResults = new ArrayList<>();
        private ModIdentity metadataIdentity;

        // The first class declaring a mod id wins, so a cache hit restores the same identity.
        private void addAnnotationIdentity(ModIdentity identity) {
            if (!this.annotationIdentities.containsKey(identity.modId())) {
                this.annotationIdentities.put(identity.modId(), identity);
            }
        }

        private JarScanResult result() {
            return JarScanResult.scanned(
                    this.mappedModIds,
                    this.metadataIdentity,
                    this.annotationIdentities.values(),
                    this.launcherModIds,
                    this.configResults);
        }
    }

    private static final class WorkerResult {
        private final List<IndexedResult> scanned;
        private final DiscoveryStatistics statistics;

        private WorkerResult(List<IndexedResult> scanned, DiscoveryStatistics statistics) {
            this.scanned = scanned;
            this.statistics = statistics;
        }
    }

    private static final class IndexedResult {
        private final int index;
        private final JarScanResult result;

        private IndexedResult(int index, JarScanResult result) {
            this.index = index;
            this.result = result;
        }
    }
}
