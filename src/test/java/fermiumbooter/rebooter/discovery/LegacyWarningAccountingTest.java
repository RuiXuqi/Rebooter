package fermiumbooter.rebooter.discovery;

import fermiumbooter.FermiumRegistryAPI;
import fermiumbooter.ForgeTestEnvironment;
import fermiumbooter.annotations.MixinConfig;
import net.minecraftforge.common.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Config(modid = "fixture", name = "legacy-warning")
    public static class WarningConfig {

        @Config.Name("Enabled")
        @MixinConfig.EarlyMixin(name = "mixins.legacy.warning.json")
        @MixinConfig.CompatHandling(modid = "minecraft", desired = false, disableMixin = false)
        public boolean enabled = true;
    }
}
