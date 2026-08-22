package fermiumbooter.rebooter.discovery;

import javax.annotation.Nullable;

final class ClassScanResult {
    private static final ClassScanResult EMPTY = new ClassScanResult(null, null);

    @Nullable
    private final DiscoveredConfig configResult;
    @Nullable
    private final ModIdentity modIdentity;

    ClassScanResult(@Nullable DiscoveredConfig configResult, @Nullable ModIdentity modIdentity) {
        this.configResult = configResult;
        this.modIdentity = modIdentity;
    }

    static ClassScanResult empty() {
        return EMPTY;
    }

    boolean hasMetadata() {
        return this.configResult != null || this.modIdentity != null;
    }

    @Nullable
    DiscoveredConfig configResult() {
        return this.configResult;
    }

    @Nullable
    ModIdentity modIdentity() {
        return this.modIdentity;
    }
}
