package fermiumbooter;

import fermiumbooter.annotations.MixinConfig;
import fermiumbooter.rebooter.RebooterCorePlugin;
import fermiumbooter.rebooter.RebooterLateLoader;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.annotation.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("deprecation")
class LegacyAnnotationRegistrationTest {

    @TempDir
    Path gameDirectory;

    @SuppressWarnings("SimplifiableAssertion")
    @Test
    void annotationsExposeHistoricalMetadataAndLegacyRegistrationReadsForgeConfig() throws Exception {
        assertEquals(RetentionPolicy.RUNTIME, MixinConfig.class.getAnnotation(Retention.class).value());
        assertEquals(ElementType.TYPE, MixinConfig.class.getAnnotation(Target.class).value()[0]);
        assertEquals(null, MixinConfig.class.getMethod("name").getDefaultValue());
        assertFieldAnnotation(MixinConfig.SubInstance.class);
        assertFieldAnnotation(MixinConfig.EarlyMixin.class);
        assertFieldAnnotation(MixinConfig.LateMixin.class);
        assertFieldAnnotation(MixinConfig.MixinToggle.class);
        assertFieldAnnotation(MixinConfig.CompatHandling.class);
        assertCompatContainer(MixinConfig.CompatHandlingContainer.class);
        assertCompatContainer(MixinConfig.CompatHandlings.class);
        assertEquals("", MixinConfig.MixinToggle.class.getMethod("earlyMixin").getDefaultValue());
        assertEquals("", MixinConfig.MixinToggle.class.getMethod("lateMixin").getDefaultValue());
        assertEquals(null, MixinConfig.CompatHandling.class.getMethod("desired").getDefaultValue());
        assertEquals(true, MixinConfig.CompatHandling.class.getMethod("disableMixin").getDefaultValue());
        assertEquals(true, MixinConfig.CompatHandling.class.getMethod("warnIngame").getDefaultValue());
        assertEquals("", MixinConfig.CompatHandling.class.getMethod("reason").getDefaultValue());
        assertEquals(
                MixinConfig.CompatHandlingContainer.class,
                MixinConfig.CompatHandling.class.getAnnotation(Repeatable.class).value());

        Path configDirectory = Files.createDirectories(this.gameDirectory.resolve("config"));
        Files.write(
                configDirectory.resolve("legacy-fixture.cfg"),
                ("general {\n" + "    B:EnabledFromFile=true\n" + "    B:NestedToggle=true\n" + "}\n")
                        .getBytes(StandardCharsets.UTF_8));
        System.setProperty("rebooter.gameDir", this.gameDirectory.toString());
        ForgeTestEnvironment.clearInjectedGameDirectory();
        assertNull(FMLInjectionData.data()[6]);
        FermiumRegistryAPI.registerAnnotatedMixinConfig(LegacyConfig.class, new LegacyConfig());

        List<String> earlyMixins = new RebooterCorePlugin().getMixinConfigs();
        assertTrue(earlyMixins.contains("mixins.legacy.enabled.json"));
        assertTrue(new RebooterLateLoader().getMixinConfigs().contains("mixins.legacy.nested.json"));
        assertFalse(earlyMixins.contains("mixins.legacy.disabled.json"));
    }

    private static void assertFieldAnnotation(Class<?> annotationType) {
        assertEquals(RetentionPolicy.RUNTIME, annotationType.getAnnotation(Retention.class).value());
        assertEquals(ElementType.FIELD, annotationType.getAnnotation(Target.class).value()[0]);
    }

    private static void assertCompatContainer(Class<?> annotationType) throws NoSuchMethodException {
        assertFieldAnnotation(annotationType);
        assertEquals(MixinConfig.CompatHandling[].class, annotationType.getMethod("value").getReturnType());
    }

    @Config(modid = "fixture", name = "legacy-fixture")
    public static class LegacyConfig {

        @Config.Name("EnabledFromFile")
        @MixinConfig.EarlyMixin(name = "mixins.legacy.enabled.json")
        public boolean enabledFromFile;

        @Config.Name("DisabledByCompat")
        @MixinConfig.EarlyMixin(name = "mixins.legacy.disabled.json")
        @MixinConfig.CompatHandling(modid = "minecraft", desired = false)
        public boolean disabledByCompat = true;

        @MixinConfig.SubInstance
        public Nested nested = new Nested();
    }

    public static class Nested {

        @Config.Name("NestedToggle")
        @MixinConfig.LateMixin(name = "mixins.legacy.nested.json")
        public boolean nestedToggle;
    }
}
