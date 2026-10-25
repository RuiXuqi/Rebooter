package fermiumbooter.rebooter.discovery;

import fermiumbooter.ForgeTestEnvironment;
import fermiumbooter.rebooter.RebooterConfigState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ModIdentityDiscoveryTest {

    private static final String FORGE_MOD = "Lnet/minecraftforge/fml/common/Mod;";
    private static final String ENABLE_MAPPINGS = "    B:overridePackageMappings=true\n";

    @TempDir
    Path gameDirectory;
    private String originalGameDirectory;

    @BeforeEach
    void prepareGameDirectory() throws Exception {
        this.originalGameDirectory = System.getProperty("rebooter.gameDir");
        ForgeTestEnvironment.setGameDirectory(this.gameDirectory);
        JarDiscovery.resetForTesting();
        RebooterConfigState.resetForTesting();
    }

    @AfterEach
    void restoreGameDirectory() {
        JarDiscovery.resetForTesting();
        RebooterConfigState.resetForTesting();
        if (this.originalGameDirectory == null) {
            System.clearProperty("rebooter.gameDir");
        } else {
            System.setProperty("rebooter.gameDir", this.originalGameDirectory);
        }
    }

    @Test
    void builtinModsCarryTheirIdentity() {
        ModIndex index = JarDiscovery.modIndex();

        assertEquals(ModIdentity.of("minecraft", "Minecraft", "1.12.2"), index.identity("minecraft"));
        assertEquals(ModIdentity.of("mcp", "Minecraft Coder Pack", "9.42"), index.identity("mcp"));
        assertEquals(ModIdentity.of("FML", "Forge Mod Loader", null), index.identity("FML"));
        assertEquals(ModIdentity.of("forge", "Minecraft Forge", null), index.identity("forge"));
    }

    @Test
    void metadataDeclaresTheIdentityOfAScannedMod() throws Exception {
        writeJar("metadata.jar", output -> {
            addEntry(output, "mcmod.info",
                    "[{\"modid\":\"metadata_mod\",\"name\":\"Metadata Mod\",\"version\":\"2.3.4\"}]");
        });

        assertEquals(
                ModIdentity.of("metadata_mod", "Metadata Mod", "2.3.4"),
                JarDiscovery.modIndex().identity("metadata_mod"));
    }

    @Test
    void modAnnotationDeclaresNameAndVersionUnlessBlank() throws Exception {
        writeJar("annotated.jar", output -> {
            addEntry(output, "fixture/Named.class", modClass("fixture/Named", "named_mod", "Named Mod", "1.4"));
            addEntry(output, "fixture/Blank.class", modClass("fixture/Blank", "blank_mod", "  ", ""));
        });

        ModIndex index = JarDiscovery.modIndex();
        assertEquals(ModIdentity.of("named_mod", "Named Mod", "1.4"), index.identity("named_mod"));
        assertEquals(ModIdentity.presenceOnly("blank_mod"), index.identity("blank_mod"));
    }

    @Test
    void aPackageMappingClearsTheMetadataIdentityAndTheModAnnotationRefillsIt() throws Exception {
        writeConfig(ENABLE_MAPPINGS
                + "    S:packageMappings <\n"
                + "        merged.marker=merged\n"
                + "    >\n");
        writeJar("merged.jar", output -> {
            addEntry(output, "mcmod.info",
                    "[{\"modid\":\"merged\",\"name\":\"From Metadata\",\"version\":\"1.0\"}]");
            addEntry(output, "merged/marker/marker.txt", "");
            addEntry(output, "fixture/Merged.class",
                    modClass("fixture/Merged", "merged", "From Annotation", null));
        });

        ModIndex index = JarDiscovery.modIndex();

        assertEquals(ModIdentity.of("merged", "From Annotation", null), index.identity("merged"));
        assertTrue(index.isPresent("MERGED"));
        assertEquals(index.identity("merged"), index.identity("MERGED"));
    }

    @Test
    void aMappedModWithoutAnnotationStaysPresentWithoutAnIdentity() throws Exception {
        writeConfig(ENABLE_MAPPINGS
                + "    S:packageMappings <\n"
                + "        mapped.marker=mapped_mod\n"
                + "    >\n");
        writeJar("mapped.jar", output -> addEntry(output, "mapped/marker/marker.txt", ""));

        ModIndex index = JarDiscovery.modIndex();

        assertTrue(index.isPresent("mapped_mod"));
        assertEquals(ModIdentity.presenceOnly("mapped_mod"), index.identity("mapped_mod"));
    }

    @Test
    void aCacheHitRestoresTheSameIdentitiesAsAFullScan() throws Exception {
        writeConfig(ENABLE_MAPPINGS
                + "    S:packageMappings <\n"
                + "        merged.marker=merged\n"
                + "    >\n");
        writeJar("cached-identities.jar", output -> {
            addEntry(output, "mcmod.info",
                    "[{\"modid\":\"merged\",\"name\":\"From Metadata\",\"version\":\"1.0\"}]");
            addEntry(output, "merged/marker/marker.txt", "");
            addEntry(output, "fixture/Merged.class",
                    modClass("fixture/Merged", "merged", "From Annotation", null));
            addEntry(output, "fixture/Other.class",
                    modClass("fixture/Other", "other_mod", "Other Mod", "5.6"));
        });
        Map<String, ModIdentity> scanned = JarDiscovery.modIndex().snapshot();
        assertTrue(JarDiscovery.getEnumeratedEntryCount() > 0);

        JarDiscovery.resetForTesting();
        Map<String, ModIdentity> restored = JarDiscovery.modIndex().snapshot();

        assertEquals(0, JarDiscovery.getEnumeratedEntryCount());
        assertEquals(scanned, restored);
    }

    private void writeConfig(String discoveryBody) throws Exception {
        Path configDirectory = Files.createDirectories(this.gameDirectory.resolve("config"));
        Files.write(
                configDirectory.resolve("rebooter.cfg"),
                ("discovery {\n" + discoveryBody + "}\n").getBytes(StandardCharsets.UTF_8));
        RebooterConfigState.resetForTesting();
        JarDiscovery.resetForTesting();
    }

    private void writeJar(String name, JarContent content) throws Exception {
        Path jar = Files.createDirectories(this.gameDirectory.resolve("mods")).resolve(name);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            content.write(output);
        }
    }

    private static void addEntry(JarOutputStream output, String name, String text) throws Exception {
        addEntry(output, name, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void addEntry(JarOutputStream output, String name, byte[] bytes) throws Exception {
        output.putNextEntry(new JarEntry(name));
        output.write(bytes);
        output.closeEntry();
    }

    private static byte[] modClass(String className, String modId, String name, String version) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);
        AnnotationVisitor annotation = writer.visitAnnotation(FORGE_MOD, true);
        annotation.visit("modid", modId);
        if (name != null) annotation.visit("name", name);
        if (version != null) annotation.visit("version", version);
        annotation.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private interface JarContent {
        void write(JarOutputStream output) throws Exception;
    }
}
