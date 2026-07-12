package fermiumbooter;

import fermiumbooter.annotations.MixinConfig;
import fermiumbooter.rebooter.RebooterCorePlugin;
import net.minecraftforge.common.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
class LegacyConfigLookupTest {

    @TempDir
    Path gameDirectory;

    @Test
    void legacyRegistrationFindsFieldKeysAcrossAllCategories() throws Exception {
        Path configDirectory = Files.createDirectories(this.gameDirectory.resolve("config"));
        Files.write(
                configDirectory.resolve("legacy-categories.cfg"),
                ("outer {\n"
                        + "    inner {\n"
                        + "        B:NamedKey=true\n"
                        + "        B:emptyName=true\n"
                        + "        B:unnamedEnabled=true\n"
                        + "        B:defaultTrueDisabled=false\n"
                        + "        B:duplicate=false\n"
                        + "        B:nestedEnabled=true\n"
                        + "    }\n"
                        + "}\n"
                        + "other {\n"
                        + "    B:duplicate=true\n"
                        + "}\n")
                        .getBytes(StandardCharsets.UTF_8));
        System.setProperty("rebooter.gameDir", this.gameDirectory.toString());
        ForgeTestEnvironment.clearInjectedGameDirectory();

        FermiumRegistryAPI.registerAnnotatedMixinConfig(LegacyConfig.class, new LegacyConfig());

        List<String> earlyMixins = new RebooterCorePlugin().getMixinConfigs();
        assertTrue(earlyMixins.contains("mixins.legacy.named.json"));
        assertTrue(earlyMixins.contains("mixins.legacy.empty-name.json"));
        assertTrue(earlyMixins.contains("mixins.legacy.unnamed.json"));
        assertTrue(earlyMixins.contains("mixins.legacy.duplicate.json"));
        assertTrue(earlyMixins.contains("mixins.legacy.missing-true.json"));
        assertTrue(earlyMixins.contains("mixins.legacy.nested.json"));
        assertFalse(earlyMixins.contains("mixins.legacy.cfg-false.json"));
        assertFalse(earlyMixins.contains("mixins.legacy.missing-false.json"));
    }

    @Config(modid = "fixture", name = "legacy-categories", category = "unused-top-level")
    public static class LegacyConfig {

        @Config.Name("NamedKey")
        @MixinConfig.EarlyMixin(name = "mixins.legacy.named.json")
        public boolean named;

        @Config.Name("")
        @MixinConfig.EarlyMixin(name = "mixins.legacy.empty-name.json")
        public boolean emptyName;

        @MixinConfig.EarlyMixin(name = "mixins.legacy.unnamed.json")
        public boolean unnamedEnabled;

        @MixinConfig.EarlyMixin(name = "mixins.legacy.cfg-false.json")
        public boolean defaultTrueDisabled = true;

        @MixinConfig.EarlyMixin(name = "mixins.legacy.duplicate.json")
        public boolean duplicate;

        @MixinConfig.EarlyMixin(name = "mixins.legacy.missing-true.json")
        public boolean missingTrue = true;

        @MixinConfig.EarlyMixin(name = "mixins.legacy.missing-false.json")
        public boolean missingFalse;

        @MixinConfig.SubInstance
        public Nested nested = new Nested();
    }

    public static class Nested {

        @MixinConfig.EarlyMixin(name = "mixins.legacy.nested.json")
        public boolean nestedEnabled;
    }
}
