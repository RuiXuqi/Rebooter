package testmod.fixture.annotation.legacy;

import fermiumbooter.FermiumRegistryAPI;
import fermiumbooter.annotations.MixinConfig;
import net.minecraftforge.common.config.Config;
import testmod.TestMod;

@SuppressWarnings("deprecation")
@Config(modid = TestMod.MOD_ID, name = "legacy-annotation", category = "annotation")
public final class LegacyAnnotationFixture {

    private static final LegacyAnnotationFixture INSTANCE = new LegacyAnnotationFixture();

    @Config.Name("Lifecycle")
    @MixinConfig.EarlyMixin(name = "testmod/mixins/annotation/legacy/lifecycle-early.json")
    @MixinConfig.LateMixin(name = "testmod/mixins/annotation/legacy/lifecycle-late.json")
    @MixinConfig.CompatHandling(modid = "minecraft", desired = true)
    public boolean lifecycle;

    @MixinConfig.SubInstance
    public final Nested nested = new Nested();

    @Config.Name("CompatibilityWarningOnly")
    @MixinConfig.LateMixin(name = "testmod/mixins/annotation/legacy/compat-warning-late.json")
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
    public boolean compatibilityWarningOnly;

    @Config.Name("CompatibilityDisabled")
    @MixinConfig.EarlyMixin(name = "testmod/mixins/annotation/legacy/compat-disabled-early.json")
    @MixinConfig.CompatHandling(
            modid = "minecraft",
            desired = false,
            warnIngame = false,
            reason = "Expected disabling mismatch")
    public boolean compatibilityDisabled;

    private LegacyAnnotationFixture() {
    }

    public static void register() {
        FermiumRegistryAPI.registerAnnotatedMixinConfig(LegacyAnnotationFixture.class, INSTANCE);
    }

    public static final class Nested {

        @Config.Name("Nested")
        @MixinConfig.LateMixin(name = "testmod/mixins/annotation/legacy/nested-late.json")
        public boolean enabled;
    }
}
