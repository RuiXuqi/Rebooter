package fermiumbooter.rebooter.discovery;

import fermiumbooter.ForgeTestEnvironment;
import fermiumbooter.rebooter.MixinRegistry;
import fermiumbooter.rebooter.RebooterCorePlugin;
import fermiumbooter.rebooter.RebooterLateLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ParallelJarDiscoveryTest {

    private static final String MIXIN_CONFIG = "Lfermiumbooter/annotations/MixinConfig;";
    private static final String MIXIN_TOGGLE = "Lfermiumbooter/annotations/MixinConfig$MixinToggle;";
    private static final int JAR_COUNT = 6;

    @TempDir
    Path gameDirectory;
    private String originalGameDirectory;

    @BeforeEach
    void prepareGameDirectory() throws Exception {
        this.originalGameDirectory = System.getProperty("rebooter.gameDir");
        writeToggleConfig();
        writeModJars();
    }

    @AfterEach
    void restoreGameDirectory() {
        if (this.originalGameDirectory == null) {
            System.clearProperty("rebooter.gameDir");
        } else {
            System.setProperty("rebooter.gameDir", this.originalGameDirectory);
        }
    }

    @Test
    void parallelScanMatchesSequentialResults() throws Exception {
        DiscoverySnapshot sequential = runDiscovery();
        DiscoverySnapshot parallel = runDiscovery();

        assertEquals(sequential.earlyMixins, parallel.earlyMixins);
        assertEquals(sequential.lateMixins, parallel.lateMixins);
        assertEquals(sequential.modIndex, parallel.modIndex);
    }

    private DiscoverySnapshot runDiscovery() throws Exception {
        System.setProperty("rebooter.gameDir", this.gameDirectory.toString());
        ForgeTestEnvironment.clearInjectedGameDirectory();
        MixinRegistry.resetForTesting();
        JarDiscovery.registerConfigs();

        List<String> early = new RebooterCorePlugin().getMixinConfigs();
        List<String> late = new RebooterLateLoader().getMixinConfigs();
        Map<String, ModIdentity> modIndex = JarDiscovery.modIndex().snapshot();
        return new DiscoverySnapshot(early, late, modIndex);
    }

    private void writeToggleConfig() throws Exception {
        Path configDirectory = Files.createDirectories(this.gameDirectory.resolve("config"));
        StringBuilder toggleConfig = new StringBuilder("feature {\n");
        for (int index = 0; index < JAR_COUNT; index++) {
            toggleConfig.append("    B:Toggle").append(index).append("=true\n");
        }
        toggleConfig.append("}\n");
        Files.write(
                configDirectory.resolve("parallel-fixture.cfg"),
                toggleConfig.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void writeModJars() throws Exception {
        Path modsDirectory = Files.createDirectories(this.gameDirectory.resolve("mods"));
        for (int index = 0; index < JAR_COUNT; index++) {
            Path jar = modsDirectory.resolve("parallel-mod-" + index + ".jar");
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
                addEntry(
                        output,
                        "mcmod.info",
                        ("[{\"modid\":\"parallel_mod_" + index + "\"}]").getBytes(StandardCharsets.UTF_8));
                addEntry(
                        output,
                        "fixture/ParallelConfig" + index + ".class",
                        configClass(index));
            }
        }
    }

    private static byte[] configClass(int index) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/ParallelConfig" + index, null, "java/lang/Object", null);
        AnnotationVisitor config = writer.visitAnnotation(MIXIN_CONFIG, true);
        config.visit("name", "parallel-fixture");
        config.visitEnd();
        FieldVisitor field = writer.visitField(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "toggle" + index, "Z", null, null);
        AnnotationVisitor toggle = field.visitAnnotation(MIXIN_TOGGLE, true);
        toggle.visit("earlyMixin", "mixins.parallel.early." + index + ".json");
        toggle.visit("lateMixin", "mixins.parallel.late." + index + ".json");
        toggle.visit("defaultValue", true);
        toggle.visitEnd();
        field.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void addEntry(JarOutputStream output, String name, byte[] bytes) throws Exception {
        output.putNextEntry(new JarEntry(name));
        output.write(bytes);
        output.closeEntry();
    }

    private static final class DiscoverySnapshot {
        private final List<String> earlyMixins;
        private final List<String> lateMixins;
        private final Map<String, ModIdentity> modIndex;

        private DiscoverySnapshot(
                List<String> earlyMixins,
                List<String> lateMixins,
                Map<String, ModIdentity> modIndex) {
            this.earlyMixins = earlyMixins;
            this.lateMixins = lateMixins;
            this.modIndex = modIndex;
        }
    }
}
