package fermiumbooter.rebooter.discovery;

import javax.annotation.Nullable;

final class CompatRule {
    private final String modid;
    @Nullable
    private final String modName;
    @Nullable
    private final String modVersionRange;
    private final boolean desired;
    private final boolean disableMixin;
    private final boolean warnIngame;
    private final String reason;

    CompatRule(
            String modid,
            @Nullable String modName,
            @Nullable String modVersionRange,
            boolean desired,
            boolean disableMixin,
            boolean warnIngame,
            String reason) {
        this.modid = modid;
        this.modName = modName;
        this.modVersionRange = modVersionRange;
        this.desired = desired;
        this.disableMixin = disableMixin;
        this.warnIngame = warnIngame;
        this.reason = reason;
    }

    String modid() {
        return this.modid;
    }

    @Nullable
    String modName() {
        return this.modName;
    }

    @Nullable
    String modVersionRange() {
        return this.modVersionRange;
    }

    boolean desired() {
        return this.desired;
    }

    boolean disableMixin() {
        return this.disableMixin;
    }

    boolean warnIngame() {
        return this.warnIngame;
    }

    String reason() {
        return this.reason;
    }
}
