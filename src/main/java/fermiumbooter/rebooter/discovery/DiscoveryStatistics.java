package fermiumbooter.rebooter.discovery;

import fermiumbooter.rebooter.Reference;

import java.util.concurrent.TimeUnit;

final class DiscoveryStatistics {
    private final boolean enabled;
    private long candidateCollectionNanos;
    private long cacheLoadNanos;
    private long cacheSaveNanos;
    private long fingerprintNanos;
    private long fingerprintBytes;
    private long entryEnumerationNanos;
    private long classFilterAndPrefilterNanos;
    private long fullClassReadAndAsmNanos;
    private long metadataNanos;
    private long configCompatNanos;
    private long candidateCount;
    private long cacheHits;
    private long cacheMisses;
    private long fingerprintCount;
    private long entryCount;
    private long prefilterCount;
    private long asmCount;
    private long metadataCount;
    private long configResultCount;

    DiscoveryStatistics(boolean enabled) {
        this.enabled = enabled;
    }

    DiscoveryStatistics newWorkerStatistics() {
        return new DiscoveryStatistics(this.enabled);
    }

    void merge(DiscoveryStatistics workerStatistics) {
        this.candidateCollectionNanos += workerStatistics.candidateCollectionNanos;
        this.cacheLoadNanos += workerStatistics.cacheLoadNanos;
        this.cacheSaveNanos += workerStatistics.cacheSaveNanos;
        this.fingerprintNanos += workerStatistics.fingerprintNanos;
        this.fingerprintBytes += workerStatistics.fingerprintBytes;
        this.entryEnumerationNanos += workerStatistics.entryEnumerationNanos;
        this.classFilterAndPrefilterNanos += workerStatistics.classFilterAndPrefilterNanos;
        this.fullClassReadAndAsmNanos += workerStatistics.fullClassReadAndAsmNanos;
        this.metadataNanos += workerStatistics.metadataNanos;
        this.configCompatNanos += workerStatistics.configCompatNanos;
        this.candidateCount += workerStatistics.candidateCount;
        this.cacheHits += workerStatistics.cacheHits;
        this.cacheMisses += workerStatistics.cacheMisses;
        this.fingerprintCount += workerStatistics.fingerprintCount;
        this.entryCount += workerStatistics.entryCount;
        this.prefilterCount += workerStatistics.prefilterCount;
        this.asmCount += workerStatistics.asmCount;
        this.metadataCount += workerStatistics.metadataCount;
        this.configResultCount += workerStatistics.configResultCount;
    }

    long start() {
        return this.enabled ? System.nanoTime() : 0L;
    }

    long elapsed(long started) {
        return this.enabled ? System.nanoTime() - started : 0L;
    }

    void candidateCollection(long nanos, int candidates) {
        if (!this.enabled) return;
        this.candidateCollectionNanos += nanos;
        this.candidateCount += candidates;
    }

    void cacheLoad(long nanos) {
        if (this.enabled) this.cacheLoadNanos += nanos;
    }

    void cacheSave(long nanos) {
        if (this.enabled) this.cacheSaveNanos += nanos;
    }

    void cacheHit() {
        if (this.enabled) this.cacheHits++;
    }

    void cacheMiss() {
        if (this.enabled) this.cacheMisses++;
    }

    void fingerprint(long bytes, long nanos) {
        this.fingerprintCount++;
        if (!this.enabled) return;
        this.fingerprintBytes += bytes;
        this.fingerprintNanos += nanos;
    }

    void fingerprintBatch(int count, long bytes, long nanos) {
        this.fingerprintCount += count;
        if (!this.enabled) return;
        this.fingerprintBytes += bytes;
        this.fingerprintNanos += nanos;
    }

    void entryEnumeration(long nanos) {
        this.entryCount++;
        if (!this.enabled) return;
        this.entryEnumerationNanos += nanos;
    }

    void classFilter(long nanos) {
        if (this.enabled) this.classFilterAndPrefilterNanos += nanos;
    }

    void prefilter(long nanos) {
        this.prefilterCount++;
        if (!this.enabled) return;
        this.classFilterAndPrefilterNanos += nanos;
    }

    void fullClassReadAndAsm(long nanos) {
        this.asmCount++;
        if (!this.enabled) return;
        this.fullClassReadAndAsmNanos += nanos;
    }

    void metadata(long nanos, boolean present) {
        if (!this.enabled) return;
        if (present) this.metadataCount++;
        this.metadataNanos += nanos;
    }

    void configCompat(long nanos, int results) {
        if (!this.enabled) return;
        this.configCompatNanos += nanos;
        this.configResultCount += results;
    }

    void logIndex() {
        if (!this.enabled) return;
        Reference.LOGGER.info(
                "Discovery stats: candidates={} collect={}ms; cache load={}ms save={}ms hits={} misses={}; "
                        + "fingerprints={} bytes={} time={}ms; entries={} enumerate={}ms; "
                        + "prefilters={} filter+cp={}ms; ASM classes={} full-read+ASM={}ms; "
                        + "metadata={} parse={}ms",
                this.candidateCount,
                millis(this.candidateCollectionNanos),
                millis(this.cacheLoadNanos),
                millis(this.cacheSaveNanos),
                this.cacheHits,
                this.cacheMisses,
                this.fingerprintCount,
                this.fingerprintBytes,
                millis(this.fingerprintNanos),
                this.entryCount,
                millis(this.entryEnumerationNanos),
                this.prefilterCount,
                millis(this.classFilterAndPrefilterNanos),
                this.asmCount,
                millis(this.fullClassReadAndAsmNanos),
                this.metadataCount,
                millis(this.metadataNanos));
    }

    void logConfig() {
        if (!this.enabled) return;
        Reference.LOGGER.info(
                "Discovery config stats: results={} config+compat={}ms",
                this.configResultCount,
                millis(this.configCompatNanos));
    }

    int fingerprintCount() {
        return (int) this.fingerprintCount;
    }

    int entryCount() {
        return (int) this.entryCount;
    }

    int prefilterCount() {
        return (int) this.prefilterCount;
    }

    int asmCount() {
        return (int) this.asmCount;
    }

    private static long millis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }
}
