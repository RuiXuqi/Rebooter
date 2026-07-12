package fermiumbooter;

import fermiumbooter.annotations.MixinConfig;
import fermiumbooter.rebooter.RebooterConfig;
import fermiumbooter.rebooter.RebooterCorePlugin;
import fermiumbooter.rebooter.discovery.JarDiscovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ModDiscoveryFallbackTest {

    @TempDir
    Path gameDirectory;

    @Test
    void configuredPackageMappingsAreDetectedBeforeMixinCompatibilityChecks() throws Exception {
        Path configDirectory = Files.createDirectories(this.gameDirectory.resolve("config"));
        Files.write(
                configDirectory.resolve("fermiumbooter.cfg"),
                ("general {\n"
                        + "    S:\"Mod Discovery Package Mappings\" <\n"
                        + "        git.jbredwards.jsonpaintings=jsonpaintings\n"
                        + "        net/jan/moddirector=moddirector\n"
                        + "        custom.marker=CustomMarker\n"
                        + "        com.google.common=embedded_guava\n"
                        + "        malformed\n"
                        + "    >\n"
                        + "}\n")
                        .getBytes(StandardCharsets.UTF_8));
        Path modsDirectory = Files.createDirectories(this.gameDirectory.resolve("mods"));
        addConfigJar(modsDirectory.resolve("a-config.jar"));
        addMarkerJar(modsDirectory.resolve("z-markers.jar"));
        System.setProperty("rebooter.gameDir", this.gameDirectory.toString());
        ForgeTestEnvironment.clearInjectedGameDirectory();

        List<String> earlyMixins = new RebooterCorePlugin().getMixinConfigs();
        Map<String, Set<String>> mappings = RebooterConfig.modDiscoveryPackageMappings();

        assertTrue(earlyMixins.contains("mixins.fallback-detected.json"));
        assertTrue(earlyMixins.contains("mixins.fallback-wrong-case.json"));
        assertEquals(java.util.Collections.singleton("CustomMarker"), mappings.get("custom/marker/"));
        assertFalse(mappings.containsKey("malformed/"));
        assertThrows(UnsupportedOperationException.class, mappings::clear);
        assertTrue(FermiumRegistryAPI.isModPresent("jsonpaintings"));
        assertTrue(FermiumRegistryAPI.isModPresent("moddirector"));
        assertTrue(FermiumRegistryAPI.isModPresent("custommarker"));
        assertTrue(FermiumRegistryAPI.isModPresent("embedded_guava"));
        assertFalse(FermiumRegistryAPI.isModPresent("optifine"));

        JarDiscovery.clear();

        assertTrue(FermiumRegistryAPI.isModPresent("embedded_guava"));
    }

    private static void addConfigJar(Path path) throws Exception {
        String resource = FallbackConfig.class.getName().replace('.', '/') + ".class";
        try (InputStream input = Objects.requireNonNull(
                FallbackConfig.class.getClassLoader().getResourceAsStream(resource), resource);
             JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry(resource));
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            output.closeEntry();
        }
    }

    private static void addMarkerJar(Path path) throws Exception {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            addMarker(output, "git/jbredwards/jsonpaintings/marker.txt");
            addMarker(output, "net/jan/moddirector/marker.txt");
            addMarker(output, "custom/marker/marker.txt");
            addMarker(output, "com/google/common/marker.txt");
            addMarker(output, "net/optifine/marker.txt");
        }
    }

    private static void addMarker(JarOutputStream output, String name) throws Exception {
        output.putNextEntry(new JarEntry(name));
        output.closeEntry();
    }

    @MixinConfig(name = "fallback-fixture")
    public static class FallbackConfig {

        @MixinConfig.MixinToggle(earlyMixin = "mixins.fallback-detected.json", defaultValue = true)
        @MixinConfig.CompatHandling(modid = "CustomMarker", desired = true)
        public static boolean enabled;

        @MixinConfig.MixinToggle(earlyMixin = "mixins.fallback-wrong-case.json", defaultValue = true)
        @MixinConfig.CompatHandling(modid = "custommarker", desired = true)
        public static boolean wrongCase;
    }
}
