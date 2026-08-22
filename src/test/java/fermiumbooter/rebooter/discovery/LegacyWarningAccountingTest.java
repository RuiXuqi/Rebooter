package fermiumbooter.rebooter.discovery;

import fermiumbooter.FermiumRegistryAPI;
import fermiumbooter.ForgeTestEnvironment;
import fermiumbooter.annotations.MixinConfig;
import fermiumbooter.rebooter.RebooterCorePlugin;
import net.minecraftforge.common.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
class LegacyWarningAccountingTest {

    @TempDir
    Path gameDirectory;

    @Test
    void legacyRegistrationTracksItsOwnWarningsAndExposesCacheInvalidation() throws Exception {
        System.setProperty("rebooter.gameDir", this.gameDirectory.toString());
        ForgeTestEnvironment.clearInjectedGameDirectory();

        FermiumRegistryAPI.registerAnnotatedMixinConfig(WarningConfig.class, new WarningConfig());

        assertEquals(1, LegacyConfigRegistrar.getWarningCount());
        LegacyConfigRegistrar.clearConfigCache();
    }

    @Test
    void legacyRegistrationAppliesTheIdentityConstraints() throws Exception {
        System.setProperty("rebooter.gameDir", this.gameDirectory.toString());
        ForgeTestEnvironment.clearInjectedGameDirectory();

        FermiumRegistryAPI.registerAnnotatedMixinConfig(IdentityConfig.class, new IdentityConfig());

        List<String> earlyMixins = new RebooterCorePlugin().getMixinConfigs();
        assertTrue(earlyMixins.contains("mixins.legacy.identity-matched.json"));
        assertFalse(earlyMixins.contains("mixins.legacy.fork.json"));
        assertEquals(1, LegacyConfigRegistrar.getWarningCount());
        LegacyConfigRegistrar.clearConfigCache();
    }

    @Config(modid = "fixture", name = "legacy-warning")
    public static class WarningConfig {

        @Config.Name("Enabled")
        @MixinConfig.EarlyMixin(name = "mixins.legacy.warning.json")
        @MixinConfig.CompatHandling(modid = "minecraft", desired = false, disableMixin = false)
        public boolean enabled = true;
    }

    @Config(modid = "fixture", name = "legacy-identity")
    public static class IdentityConfig {

        @Config.Name("IdentityMatched")
        @MixinConfig.EarlyMixin(name = "mixins.legacy.identity-matched.json")
        @MixinConfig.CompatHandling(
                modid = "minecraft",
                modName = "Minecraft",
                targetVersionRange = "[1.12,1.13)",
                desired = true)
        public boolean identityMatched = true;

        @Config.Name("ForkDisabled")
        @MixinConfig.EarlyMixin(name = "mixins.legacy.fork.json")
        @MixinConfig.CompatHandling(modid = "minecraft", modName = "Not Minecraft", desired = true)
        public boolean forkDisabled = true;
    }
}
