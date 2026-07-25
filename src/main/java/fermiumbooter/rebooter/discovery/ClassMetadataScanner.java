package fermiumbooter.rebooter.discovery;

import java.io.IOException;
import java.io.InputStream;

final class ClassMetadataScanner {
    private final ClassDescriptorPrefilter prefilter = new ClassDescriptorPrefilter();
    private final DiscoveryStatistics statistics;

    ClassMetadataScanner() {
        this(new DiscoveryStatistics());
    }

    ClassMetadataScanner(DiscoveryStatistics statistics) {
        this.statistics = statistics;
    }

    static String cacheProfile() {
        return ClassDescriptorPrefilter.cacheProfile()
                + AsmClassMetadataReader.cacheProfile();
    }

    ClassScanResult scan(InputStream input, long classSize) throws IOException {
        long prefilterStarted = this.statistics.start();
        boolean prefilterTimingRecorded = false;
        try {
            ClassDescriptorPrefilter.Match prefiltered = this.prefilter.scan(input, classSize);
            long prefilterNanos = Math.max(
                    0L,
                    this.statistics.elapsed(prefilterStarted) - prefiltered.fullClassReadNanos());
            this.statistics.prefilter(prefilterNanos);
            prefilterTimingRecorded = true;
            if (prefiltered.isEmpty()) {
                return ClassScanResult.empty();
            }
            long asmStarted = this.statistics.start();
            try {
                return AsmClassMetadataReader.read(
                        prefiltered.classBytes(),
                        prefiltered.annotationKinds());
            } finally {
                this.statistics.fullClassReadAndAsm(
                        prefiltered.fullClassReadNanos() + this.statistics.elapsed(asmStarted));
            }
        } finally {
            if (!prefilterTimingRecorded) {
                this.statistics.prefilter(this.statistics.elapsed(prefilterStarted));
            }
        }
    }

}
