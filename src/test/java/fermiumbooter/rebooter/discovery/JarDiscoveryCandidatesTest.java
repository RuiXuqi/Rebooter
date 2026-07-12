package fermiumbooter.rebooter.discovery;

import fermiumbooter.ForgeTestEnvironment;
import net.minecraft.launchwrapper.Launch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JarDiscoveryCandidatesTest {

    @TempDir
    Path gameDirectory;
    private final String originalClassPath = System.getProperty("java.class.path");
    private final String originalGameDirectory = System.getProperty("rebooter.gameDir");
    private final Map<String, Object> originalBlackboard = Launch.blackboard;

    @AfterEach
    void restoreLaunchEnvironment() {
        restoreProperty("java.class.path", this.originalClassPath);
        restoreProperty("rebooter.gameDir", this.originalGameDirectory);
        Launch.blackboard = this.originalBlackboard;
    }

    @Test
    void mergesStandardAndClasspathCandidatesWithoutDuplicates() throws Exception {
        Path modsDirectory = Files.createDirectories(this.gameDirectory.resolve("mods"));
        Path rootJar = createJar(modsDirectory.resolve("root.jar"));
        Path versionJar = createJar(Files.createDirectories(modsDirectory.resolve("1.12.2")).resolve("version.jar"));
        Path commandLineJar = createJar(this.gameDirectory.resolve("command-line.jar"));
        Path containedJar = createContainedDependency(modsDirectory);
        Path classPathJar = createJar(this.gameDirectory.resolve("classpath.jar"));
        System.setProperty(
                "java.class.path",
                rootJar + File.pathSeparator + classPathJar);
        Launch.blackboard = new HashMap<>();
        Map<String, String> forgeLaunchArgs = new HashMap<>();
        forgeLaunchArgs.put("--mods", this.gameDirectory.relativize(commandLineJar).toString());
        Launch.blackboard.put("forgeLaunchArgs", forgeLaunchArgs);
        ForgeTestEnvironment.setGameDirectory(this.gameDirectory);

        Set<File> expected = new LinkedHashSet<>();
        expected.add(rootJar.toFile().getCanonicalFile());
        expected.add(versionJar.toFile().getCanonicalFile());
        expected.add(commandLineJar.toFile().getCanonicalFile());
        expected.add(containedJar.toFile().getCanonicalFile());
        expected.add(classPathJar.toFile().getCanonicalFile());

        assertEquals(expected, JarDiscovery.candidates());
    }

    private static Path createJar(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(path))) {
        }
        return path;
    }

    private static Path createContainedDependency(Path modsDirectory) throws Exception {
        Files.write(
                modsDirectory.resolve("mod_list.json"),
                ("{\n"
                        + "  \"repositoryRoot\": \"mods/memory_repo\",\n"
                        + "  \"modRef\": [\"example:contained:1.0\"]\n"
                        + "}\n").getBytes(StandardCharsets.UTF_8));
        return createJar(modsDirectory.resolve("memory_repo/example/contained/1.0/contained-1.0.jar"));
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
