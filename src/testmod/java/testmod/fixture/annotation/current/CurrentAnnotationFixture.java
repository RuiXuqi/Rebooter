package testmod.fixture.annotation.current;

import fermiumbooter.annotations.MixinConfig;
import net.minecraftforge.common.config.Config;

@MixinConfig(name = "current-annotation")
public final class CurrentAnnotationFixture {

    @Config.Name("Lifecycle")
    @MixinConfig.MixinToggle(
            earlyMixin = "testmod/mixins/annotation/current/mixins.lifecycle-early.json",
            lateMixin = "testmod/mixins/annotation/current/mixins.lifecycle-late.json",
            defaultValue = false)
    public static boolean lifecycle;

    @Config.Name("DefaultEnabled")
    @MixinConfig.MixinToggle(
            lateMixin = "testmod/mixins/annotation/current/mixins.default-late.json",
            defaultValue = true)
    public static boolean defaultEnabled;

    @Config.Name("CompatibilityWarningOnly")
    @MixinConfig.MixinToggle(
            lateMixin = "testmod/mixins/annotation/current/mixins.compat-warning-late.json",
            defaultValue = true)
    @MixinConfig.CompatHandling(
            modid = "minecraft",
            desired = false,
            disableMixin = false,
            warnIngame = false,
            reason = "Expected warning-only mismatch")
    @MixinConfig.CompatHandling(
            modid = "forge",
            desired = true,
            reason = "Forge must be available")
    public static boolean compatibilityWarningOnly;

    @Config.Name("CompatibilityDisabled")
    @MixinConfig.MixinToggle(
            lateMixin = "testmod/mixins/annotation/current/mixins.compat-disabled-late.json",
            defaultValue = true)
    @MixinConfig.CompatHandling(
            modid = "minecraft",
            desired = false,
            warnIngame = false,
            reason = "Expected disabling mismatch")
    public static boolean compatibilityDisabled;

    @Config.Name("IdentityMatched")
    @MixinConfig.MixinToggle(
            lateMixin = "testmod/mixins/annotation/current/mixins.identity-matched-late.json",
            defaultValue = true)
    @MixinConfig.CompatHandling(
            modid = "minecraft",
            modName = "Minecraft",
            targetVersionRange = "[1.12,1.13)",
            desired = true,
            reason = "Minecraft 1.12 must be the running game")
    public static boolean identityMatched;

    @Config.Name("ForkDisabled")
    @MixinConfig.MixinToggle(
            lateMixin = "testmod/mixins/annotation/current/mixins.fork-disabled-late.json",
            defaultValue = true)
    @MixinConfig.CompatHandling(
            modid = "minecraft",
            modName = "Not Minecraft",
            desired = true,
            warnIngame = false,
            reason = "Expected fork mismatch")
    public static boolean forkDisabled;

    @Config.Name("VersionWarningOnly")
    @MixinConfig.MixinToggle(
            lateMixin = "testmod/mixins/annotation/current/mixins.version-warning-late.json",
            defaultValue = true)
    @MixinConfig.CompatHandling(
            modid = "minecraft",
            targetVersionRange = "[2.0,)",
            desired = true,
            disableMixin = false,
            warnIngame = false,
            reason = "Expected version mismatch")
    public static boolean versionWarningOnly;

    @Config.Name("MixinSquared")
    @MixinConfig.MixinToggle(
            lateMixin = "testmod/mixins/feature/mixins.mixinsquared.json",
            defaultValue = false)
    public static boolean mixinSquared;

    private CurrentAnnotationFixture() {
    }
}
