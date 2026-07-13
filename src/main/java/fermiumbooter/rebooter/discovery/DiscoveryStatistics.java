package fermiumbooter.rebooter.discovery;

import fermiumbooter.rebooter.Reference;

import java.util.concurrent.TimeUnit;

final class DiscoveryStatistics {
    private static final String ENABLED_PROPERTY = "rebooter.discoveryStats";
    private final boolean enabled = Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"));
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
    private int candidateCount;
    private int cacheHits;
    private int cacheMisses;
    private int fingerprintCount;
    private int entryCount;
    private int prefilterCount;
    private int asmCount;
    private int metadataCount;
    private int configResultCount;

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
        if (!this.enabled) return;
        this.fingerprintCount++;
        this.fingerprintBytes += bytes;
        this.fingerprintNanos += nanos;
    }

    void fingerprintBatch(int count, long bytes, long nanos) {
        if (!this.enabled) return;
        this.fingerprintCount += count;
        this.fingerprintBytes += bytes;
        this.fingerprintNanos += nanos;
    }

    void entryEnumeration(long nanos) {
        if (!this.enabled) return;
        this.entryCount++;
        this.entryEnumerationNanos += nanos;
    }

    void classFilterAndPrefilter(long nanos, boolean prefiltered) {
        if (!this.enabled) return;
        if (prefiltered) this.prefilterCount++;
        this.classFilterAndPrefilterNanos += nanos;
    }

    void fullClassReadAndAsm(long nanos) {
        if (!this.enabled) return;
        this.asmCount++;
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

    private static long millis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }
}
