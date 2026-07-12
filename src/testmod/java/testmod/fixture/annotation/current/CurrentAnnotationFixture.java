package testmod.fixture.annotation.current;

import fermiumbooter.annotations.MixinConfig;
import net.minecraftforge.common.config.Config;

@MixinConfig(name = "current-annotation")
public final class CurrentAnnotationFixture {

    @Config.Name("Lifecycle")
    @MixinConfig.MixinToggle(
            earlyMixin = "testmod/mixins/annotation/current/lifecycle-early.json",
            lateMixin = "testmod/mixins/annotation/current/lifecycle-late.json",
            defaultValue = false)
    public static boolean lifecycle;

    @Config.Name("DefaultEnabled")
    @MixinConfig.MixinToggle(
            lateMixin = "testmod/mixins/annotation/current/default-late.json",
            defaultValue = true)
    public static boolean defaultEnabled;

    @Config.Name("CompatibilityWarningOnly")
    @MixinConfig.MixinToggle(
            lateMixin = "testmod/mixins/annotation/current/compat-warning-late.json",
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
            lateMixin = "testmod/mixins/annotation/current/compat-disabled-late.json",
            defaultValue = true)
    @MixinConfig.CompatHandling(
            modid = "minecraft",
            desired = false,
            warnIngame = false,
            reason = "Expected disabling mismatch")
    public static boolean compatibilityDisabled;

    @Config.Name("MixinSquared")
    @MixinConfig.MixinToggle(
            lateMixin = "testmod/mixins/feature/mixinsquared.json",
            defaultValue = false)
    public static boolean mixinSquared;

    private CurrentAnnotationFixture() {
    }
}
