package fermiumbooter.rebooter.discovery;

import fermiumbooter.ForgeTestEnvironment;
import fermiumbooter.annotations.MixinConfig;
import fermiumbooter.rebooter.RebooterCorePlugin;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ClasspathExposureTest {

    @TempDir
    Path gameDirectory;

    @AfterEach
    void clearLaunchClassLoader() {
        Launch.classLoader = null;
    }

    @Test
    void jarsDeclaringDiscoveredTogglesAreExposedToTheLaunchClasspath() throws Exception {
        Launch.classLoader = new LaunchClassLoader(new URL[0]);
        Path modsDirectory = Files.createDirectories(this.gameDirectory.resolve("mods"));
        Path configJar = modsDirectory.resolve("exposed-config.jar");
        Path plainJar = modsDirectory.resolve("plain.jar");
        writeClassJar(configJar, ExposureFixture.class);
        writeClassJar(plainJar, PlainFixture.class);
        System.setProperty("rebooter.gameDir", this.gameDirectory.toString());
        ForgeTestEnvironment.clearInjectedGameDirectory();

        String configJarUrl = configJar.toUri().toURL().toExternalForm();
        assertFalse(onLaunchClasspath(configJarUrl), "fixture jar unexpectedly started on the launch classpath");

        new RebooterCorePlugin().getMixinConfigs();

        assertTrue(onLaunchClasspath(configJarUrl), "@MixinConfig jar should be added to the launch classpath");
        String plainJarUrl = plainJar.toUri().toURL().toExternalForm();
        assertFalse(onLaunchClasspath(plainJarUrl), "jars without discovered toggles must stay off the launch classpath");
    }

    private static boolean onLaunchClasspath(String url) {
        if (Launch.classLoader == null) {
            return false;
        }
        for (URL entry : Launch.classLoader.getURLs()) {
            if (entry.toExternalForm().equalsIgnoreCase(url)) {
                return true;
            }
        }
        return false;
    }

    private static void writeClassJar(Path path, Class<?> fixture) throws Exception {
        String resource = fixture.getName().replace('.', '/') + ".class";
        try (InputStream input = Objects.requireNonNull(
                fixture.getClassLoader().getResourceAsStream(resource), resource);
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

    @MixinConfig(name = "exposure-fixture")
    public static class ExposureFixture {

        @MixinConfig.MixinToggle(earlyMixin = "mixins.exposure.json", defaultValue = true)
        public static boolean enabled;
    }

    public static class PlainFixture {
    }
}
