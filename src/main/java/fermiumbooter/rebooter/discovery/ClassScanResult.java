package fermiumbooter.rebooter.discovery;

import javax.annotation.Nullable;

final class ClassScanResult {
    private static final ClassScanResult EMPTY = new ClassScanResult(null, null);

    @Nullable
    private final DiscoveredConfig configResult;
    @Nullable
    private final String modId;

    ClassScanResult(@Nullable DiscoveredConfig configResult, @Nullable String modId) {
        this.configResult = configResult;
        this.modId = modId;
    }

    static ClassScanResult empty() {
        return EMPTY;
    }

    boolean hasMetadata() {
        return this.configResult != null || this.modId != null;
    }

    @Nullable
    DiscoveredConfig configResult() {
        return this.configResult;
    }

    @Nullable
    String modId() {
        return this.modId;
    }
}
