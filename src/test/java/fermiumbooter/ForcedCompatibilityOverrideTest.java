package fermiumbooter;

import fermiumbooter.annotations.MixinConfig;
import fermiumbooter.rebooter.RebooterCorePlugin;
import net.minecraftforge.common.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
class ForcedCompatibilityOverrideTest {

    @TempDir
    Path gameDirectory;

    @Test
    void forcedOverrideAllowsACompatibilityFailure() throws Exception {
        Path configDirectory = Files.createDirectories(this.gameDirectory.resolve("config"));
        Files.write(
                configDirectory.resolve("fermiumbooter.cfg"),
                ("general {\n"
                        + "    B:\"Override Mixin Config Compatibility Checks\"=true\n"
                        + "}\n")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(
                configDirectory.resolve("override-fixture.cfg"),
                ("general {\n" + "    B:Enabled=true\n" + "}\n").getBytes(StandardCharsets.UTF_8));
        ForgeTestEnvironment.setGameDirectory(this.gameDirectory);

        FermiumRegistryAPI.registerAnnotatedMixinConfig(OverrideConfig.class, null);

        assertTrue(new RebooterCorePlugin().getMixinConfigs().contains("mixins.override.json"));
    }

    @Config(modid = "fixture", name = "override-fixture")
    public static class OverrideConfig {

        @Config.Name("Enabled")
        @MixinConfig.EarlyMixin(name = "mixins.override.json")
        @MixinConfig.CompatHandling(modid = "minecraft", desired = false)
        public static boolean enabled;
    }
}
