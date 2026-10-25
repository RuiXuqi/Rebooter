package fermiumbooter;

import fermiumbooter.rebooter.RebooterConfig;
import fermiumbooter.rebooter.RebooterCorePlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class RebooterMalformedConfigTest {

    @TempDir
    Path gameDirectory;

    @Test
    void malformedConfigIsBackedUpAndReplacedWithForgeDefaults() throws Exception {
        Path configDirectory = Files.createDirectories(this.gameDirectory.resolve("config"));
        Path configFile = configDirectory.resolve("rebooter.cfg");
        Files.write(
                configFile,
                ("general {\n" + "}\n" + "}\n")
                        .getBytes(StandardCharsets.UTF_8));
        System.setProperty("rebooter.gameDir", this.gameDirectory.toString());
        ForgeTestEnvironment.clearInjectedGameDirectory();

        assertEquals(
                Collections.singletonList("mixins.fermiumbooter.init.json"),
                new RebooterCorePlugin().getMixinConfigs());

        assertArrayEquals(new String[0], RebooterConfig.mixins.forceLoadConfigs);
        assertArrayEquals(
                new String[]{
                        "git.jbredwards.jsonpaintings=jsonpaintings",
                        "net.jan.moddirector=moddirector",
                        "gg.essential=essential"
                },
                RebooterConfig.discovery.packageMappings);
        assertTrue(Files.readAllLines(configFile, StandardCharsets.UTF_8)
                .contains("    B:ignoreCompatChecks=false"));
        try (java.util.stream.Stream<Path> files = Files.list(configDirectory)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().matches(
                    "rebooter\\.cfg_\\d{8}_\\d{6}\\.errored")));
        }
    }
}
