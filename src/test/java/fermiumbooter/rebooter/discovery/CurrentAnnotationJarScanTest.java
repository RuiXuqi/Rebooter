package fermiumbooter.rebooter.discovery;

import fermiumbooter.ForgeTestEnvironment;
import fermiumbooter.annotations.MixinConfig;
import fermiumbooter.rebooter.RebooterCorePlugin;
import fermiumbooter.rebooter.RebooterLateLoader;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class CurrentAnnotationJarScanTest {

    private static final String MIXIN_CONFIG = "Lfermiumbooter/annotations/MixinConfig;";
    private static final String MIXIN_TOGGLE = "Lfermiumbooter/annotations/MixinConfig$MixinToggle;";
    private static final String COMPAT_HANDLING = "Lfermiumbooter/annotations/MixinConfig$CompatHandling;";
    private static final String COMPAT_HANDLINGS = "Lfermiumbooter/annotations/MixinConfig$CompatHandlings;";
    private static final String CONFIG_NAME = "Lnet/minecraftforge/common/config/Config$Name;";

    @TempDir
    Path gameDirectory;

    @Test
    void scannerReadsCurrentAnnotationsFromJarBytecode() throws Exception {
        Path configDirectory = Files.createDirectories(this.gameDirectory.resolve("config"));
        Files.write(
                configDirectory.resolve("current-fixture.cfg"),
                ("feature {\n"
                        + "    B:CurrentToggle=true\n"
                        + "    B:DisabledToggle=true\n"
                        + "    B:HistoricalCompat=true\n"
                        + "    B:LegacyStyle=true\n"
                        + "    B:fieldNamed=true\n"
                        + "}\n")
                        .getBytes(StandardCharsets.UTF_8));
        Path fixtureJar = Files.createDirectories(this.gameDirectory.resolve("mods")).resolve("current-fixture.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(fixtureJar))) {
            output.putNextEntry(new JarEntry("broken/Truncated.class"));
            output.write(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
            output.closeEntry();
            addClass(output, CurrentFixture.class);
            addClass(output, EmptyNameFixture.class);
            addHistoricalCompatClass(output);
        }

        System.setProperty("rebooter.gameDir", this.gameDirectory.toString());
        ForgeTestEnvironment.clearInjectedGameDirectory();
        assertNull(FMLInjectionData.data()[6]);
        JarDiscovery.registerConfigs();

        java.util.List<String> earlyMixins = new RebooterCorePlugin().getMixinConfigs();
        assertTrue(earlyMixins.contains("mixins.current.early.json"));
        assertTrue(earlyMixins.contains("mixins.current.field-name.json"));
        assertEquals(0, JarDiscovery.getWarningCount());
        assertTrue(new RebooterLateLoader().getMixinConfigs().contains("mixins.current.late.json"));
        assertFalse(earlyMixins.contains("mixins.current.disabled.json"));
        assertFalse(earlyMixins.contains("mixins.current.historical-compat.json"));
        assertFalse(earlyMixins.contains("mixins.current.nonboolean.json"));
        assertFalse(earlyMixins.contains("mixins.current.legacy-style.json"));
        assertFalse(earlyMixins.contains("mixins.current.empty-name.json"));
    }

    private static void addClass(JarOutputStream output, Class<?> type) throws Exception {
        String classResource = type.getName().replace('.', '/') + ".class";
        try (InputStream input = Objects.requireNonNull(
                type.getClassLoader().getResourceAsStream(classResource), classResource)) {
            output.putNextEntry(new JarEntry(classResource));
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            output.closeEntry();
        }
    }

    private static void addHistoricalCompatClass(JarOutputStream output) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/HistoricalCompat", null, "java/lang/Object", null);

        AnnotationVisitor config = writer.visitAnnotation(MIXIN_CONFIG, true);
        config.visit("name", "current-fixture");
        config.visitEnd();

        FieldVisitor field = writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "enabled", "Z", null, null);
        AnnotationVisitor configName = field.visitAnnotation(CONFIG_NAME, true);
        configName.visit("value", "HistoricalCompat");
        configName.visitEnd();
        AnnotationVisitor toggle = field.visitAnnotation(MIXIN_TOGGLE, true);
        toggle.visit("earlyMixin", "mixins.current.historical-compat.json");
        toggle.visit("defaultValue", false);
        toggle.visitEnd();

        AnnotationVisitor container = field.visitAnnotation(COMPAT_HANDLINGS, true);
        AnnotationVisitor rules = container.visitArray("value");
        AnnotationVisitor compatible = rules.visitAnnotation(null, COMPAT_HANDLING);
        compatible.visit("modid", "minecraft");
        compatible.visit("desired", true);
        compatible.visitEnd();
        AnnotationVisitor incompatible = rules.visitAnnotation(null, COMPAT_HANDLING);
        incompatible.visit("modid", "forge");
        incompatible.visit("desired", false);
        incompatible.visit("warnIngame", false);
        incompatible.visitEnd();
        rules.visitEnd();
        container.visitEnd();
        field.visitEnd();
        writer.visitEnd();

        output.putNextEntry(new JarEntry("fixture/HistoricalCompat.class"));
        output.write(writer.toByteArray());
        output.closeEntry();
    }

    @SuppressWarnings("deprecation")
    @MixinConfig(name = "current-fixture")
    public static class CurrentFixture {

        @Config.Name("CurrentToggle")
        @MixinConfig.MixinToggle(
                earlyMixin = "mixins.current.early.json",
                lateMixin = "mixins.current.late.json",
                defaultValue = false)
        public static boolean enabled;

        @Config.Name("DisabledToggle")
        @MixinConfig.MixinToggle(earlyMixin = "mixins.current.disabled.json", defaultValue = true)
        @MixinConfig.CompatHandling(modid = "forge", desired = false, warnIngame = false)
        @MixinConfig.CompatHandling(modid = "minecraft", desired = true)
        public static boolean disabled;

        @Config.Name("NonBooleanToggle")
        @MixinConfig.MixinToggle(earlyMixin = "mixins.current.nonboolean.json", defaultValue = true)
        public static String nonBoolean;

        @Config.Name("LegacyStyle")
        @MixinConfig.MixinToggle(defaultValue = true)
        @MixinConfig.EarlyMixin(name = "mixins.current.legacy-style.json")
        public static boolean legacyStyle;

        @MixinConfig.MixinToggle(earlyMixin = "mixins.current.field-name.json", defaultValue = false)
        public static boolean fieldNamed;
    }

    @MixinConfig(name = "")
    public static class EmptyNameFixture {

        @Config.Name("EmptyName")
        @MixinConfig.MixinToggle(earlyMixin = "mixins.current.empty-name.json", defaultValue = true)
        public static boolean enabled;
    }
}
