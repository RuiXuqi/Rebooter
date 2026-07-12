package fermiumbooter.rebooter.discovery;

final class CompatRule {
    private final String modid;
    private final boolean desired;
    private final boolean disableMixin;
    private final boolean warnIngame;
    private final String reason;

    CompatRule(String modid, boolean desired, boolean disableMixin, boolean warnIngame, String reason) {
        this.modid = modid;
        this.desired = desired;
        this.disableMixin = disableMixin;
        this.warnIngame = warnIngame;
        this.reason = reason;
    }

    String modid() {
        return this.modid;
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
