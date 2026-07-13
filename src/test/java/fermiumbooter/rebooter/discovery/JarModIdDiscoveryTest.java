package fermiumbooter.rebooter.discovery;

import fermiumbooter.FermiumRegistryAPI;
import fermiumbooter.ForgeTestEnvironment;
import fermiumbooter.config.FermiumBooterConfig;
import fermiumbooter.rebooter.RebooterConfig;
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
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

class JarModIdDiscoveryTest {

    private static final String FORGE_MOD = "Lnet/minecraftforge/fml/common/Mod;";

    @TempDir
    Path gameDirectory;
    private String originalGameDirectory;
    private String[] originalDiscoveryScanAllowlist;

    @BeforeEach
    void prepareGameDirectory() throws Exception {
        this.originalGameDirectory = System.getProperty("rebooter.gameDir");
        this.originalDiscoveryScanAllowlist = FermiumBooterConfig.discoveryClassScanAllowlist.clone();
        ForgeTestEnvironment.setGameDirectory(this.gameDirectory);
        JarDiscovery.resetForTesting();
        RebooterConfig.resetForTesting();
    }

    @AfterEach
    void restoreGameDirectory() {
        JarDiscovery.resetForTesting();
        FermiumBooterConfig.discoveryClassScanAllowlist = this.originalDiscoveryScanAllowlist;
        RebooterConfig.resetForTesting();
        if (this.originalGameDirectory == null) {
            System.clearProperty("rebooter.gameDir");
        } else {
            System.setProperty("rebooter.gameDir", this.originalGameDirectory);
        }
    }

    @Test
    void unionsTheFirstValidMetadataIdWithEveryModAnnotation() throws Exception {
        Path jar = Files.createDirectories(this.gameDirectory.resolve("mods")).resolve("multiple-mods.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(output, "mcmod.info", ("[\n"
                    + "  {\"modid\": 12},\n"
                    + "  {\"modid\": \"  \"},\n"
                    + "  {\"modid\": \"metadata_first\"},\n"
                    + "  {\"modid\": \"metadata_ignored\"}\n"
                    + "]\n").getBytes(StandardCharsets.UTF_8));
            addEntry(output, "fixture/FirstMod.class", modClass("fixture/FirstMod", "annotation_one"));
            addEntry(output, "fixture/SecondMod.class", modClass("fixture/SecondMod", "annotation_two"));
        }

        assertTrue(FermiumRegistryAPI.isModPresent("METADATA_FIRST"));
        assertTrue(FermiumRegistryAPI.isModPresent("annotation_one"));
        assertTrue(FermiumRegistryAPI.isModPresent("ANNOTATION_TWO"));
        assertFalse(FermiumRegistryAPI.isModPresent("metadata_ignored"));
    }

    @Test
    void recognizesOptiFineFromItsForgeTweakerManifest() throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("TweakClass", "optifine.OptiFineForgeTweaker");
        Path jar = Files.createDirectories(this.gameDirectory.resolve("mods")).resolve("OptiFine.jar");
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
        }

        assertTrue(FermiumRegistryAPI.isModPresent("OptiFine"));
    }

    @Test
    void normalMixinRegistryCleanupKeepsTheCompletedModIndex() throws Exception {
        Path jar = Files.createDirectories(this.gameDirectory.resolve("mods")).resolve("persistent-index.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(output, "mcmod.info", "[{\"modid\":\"persistent_index\"}]".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(FermiumRegistryAPI.isModPresent("persistent_index"));

        Files.delete(jar);
        JarDiscovery.clear();

        assertTrue(FermiumRegistryAPI.isModPresent("persistent_index"));
    }

    @Test
    void classesWithoutRelevantConstantPoolDescriptorsNeverEnterAsm() throws Exception {
        Path jar = Files.createDirectories(this.gameDirectory.resolve("mods")).resolve("prefilter.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(output, "fixture/Plain.class", plainClass("fixture/Plain"));
            addEntry(output, "fixture/Mod.class", modClass("fixture/Mod", "prefilter_mod"));
        }

        assertTrue(FermiumRegistryAPI.isModPresent("prefilter_mod"));
        assertEquals(2, JarDiscovery.getPrefilterScanCount());
        assertEquals(1, JarDiscovery.getAsmClassReadCount());
    }

    @Test
    void warmCacheRestoresMetadataAndAnnotationIdsWithoutEnumeratingTheJar() throws Exception {
        Path jar = Files.createDirectories(this.gameDirectory.resolve("mods")).resolve("cached-mod-ids.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("TweakClass", "optifine.OptiFineForgeTweaker");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            addEntry(output, "mcmod.info", "[{\"modid\":\"cached_metadata\"}]".getBytes(StandardCharsets.UTF_8));
            addEntry(output, "fixture/CachedMod.class", modClass("fixture/CachedMod", "cached_annotation"));
            addEntry(output, "git/jbredwards/jsonpaintings/marker.txt", new byte[0]);
        }
        assertTrue(FermiumRegistryAPI.isModPresent("cached_metadata"));
        assertTrue(FermiumRegistryAPI.isModPresent("cached_annotation"));
        assertTrue(FermiumRegistryAPI.isModPresent("optifine"));
        assertTrue(FermiumRegistryAPI.isModPresent("jsonpaintings"));
        assertTrue(JarDiscovery.getEnumeratedEntryCount() > 0);

        JarDiscovery.resetForTesting();

        assertTrue(FermiumRegistryAPI.isModPresent("cached_metadata"));
        assertTrue(FermiumRegistryAPI.isModPresent("cached_annotation"));
        assertTrue(FermiumRegistryAPI.isModPresent("optifine"));
        assertTrue(FermiumRegistryAPI.isModPresent("jsonpaintings"));
        assertEquals(0, JarDiscovery.getEnumeratedEntryCount());
        assertEquals(0, JarDiscovery.getPrefilterScanCount());
    }

    @Test
    void invalidMetadataAndDamagedClassesDoNotBlockHealthyModAnnotations() throws Exception {
        Path jar = Files.createDirectories(this.gameDirectory.resolve("mods")).resolve("damaged-entries.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(output, "mcmod.info", "[{not valid json]".getBytes(StandardCharsets.UTF_8));
            addEntry(output, "fixture/Damaged.class", new byte[]{0, 1, 2, 3});
            addEntry(output, "fixture/Healthy.class", modClass("fixture/Healthy", "healthy_mod"));
        }

        assertTrue(FermiumRegistryAPI.isModPresent("healthy_mod"));
    }

    @Test
    void changedJarInvalidatesCachedModIds() throws Exception {
        Path jar = Files.createDirectories(this.gameDirectory.resolve("mods")).resolve("changed-id.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(output, "fixture/Old.class", modClass("fixture/Old", "old_mod_id"));
        }
        assertTrue(FermiumRegistryAPI.isModPresent("old_mod_id"));

        JarDiscovery.resetForTesting();
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(output, "fixture/New.class", modClass("fixture/New", "new_mod_id"));
            addEntry(output, "changed/marker.txt", new byte[]{1});
        }

        assertTrue(FermiumRegistryAPI.isModPresent("new_mod_id"));
        assertFalse(FermiumRegistryAPI.isModPresent("old_mod_id"));
        assertTrue(JarDiscovery.getPrefilterScanCount() > 0);
    }

    @Test
    void allowlistMakesAnOtherwiseFilteredModClassScannable() throws Exception {
        Path defaultGame = Files.createDirectories(this.gameDirectory.resolve("default"));
        addFilteredModJar(defaultGame);
        ForgeTestEnvironment.setGameDirectory(defaultGame);
        RebooterConfig.resetForTesting();
        JarDiscovery.resetForTesting();

        assertFalse(FermiumRegistryAPI.isModPresent("allowlisted_mod"));

        Path allowedGame = Files.createDirectories(this.gameDirectory.resolve("allowed"));
        addFilteredModJar(allowedGame);
        Path configDirectory = Files.createDirectories(allowedGame.resolve("config"));
        Files.write(
                configDirectory.resolve("fermiumbooter.cfg"),
                ("general {\n"
                        + "    S:\"Discovery Class Scan Allowlist\" <\n"
                        + "        com.google.common\n"
                        + "    >\n"
                        + "}\n").getBytes(StandardCharsets.UTF_8));
        ForgeTestEnvironment.setGameDirectory(allowedGame);
        RebooterConfig.resetForTesting();
        JarDiscovery.resetForTesting();

        assertTrue(FermiumRegistryAPI.isModPresent("allowlisted_mod"));
    }

    private static void addFilteredModJar(Path gameDirectory) throws Exception {
        Path jar = Files.createDirectories(gameDirectory.resolve("mods")).resolve("filtered.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(
                    output,
                    "com/google/common/FilteredMod.class",
                    modClass("com/google/common/FilteredMod", "allowlisted_mod"));
        }
    }

    private static byte[] modClass(String className, String modId) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);
        AnnotationVisitor annotation = writer.visitAnnotation(FORGE_MOD, true);
        annotation.visit("modid", modId);
        annotation.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] plainClass(String className) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void addEntry(JarOutputStream output, String name, byte[] bytes) throws Exception {
        output.putNextEntry(new JarEntry(name));
        output.write(bytes);
        output.closeEntry();
    }
}
