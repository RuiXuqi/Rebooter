package fermiumbooter;

import fermiumbooter.rebooter.RebooterCorePlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModDiscoveryEarlyQueryTest {

    @TempDir
    Path gameDirectory;

    @Test
    void presenceQueryScansFallbacksWithoutPreparingMixinRegistry() throws Exception {
        Path modsDirectory = Files.createDirectories(this.gameDirectory.resolve("mods"));
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(modsDirectory.resolve("marker.jar")))) {
            output.putNextEntry(new JarEntry("git/jbredwards/jsonpaintings/marker.txt"));
            output.closeEntry();
        }
        System.setProperty("rebooter.gameDir", this.gameDirectory.toString());
        ForgeTestEnvironment.clearInjectedGameDirectory();
        AtomicInteger evaluations = new AtomicInteger();
        FermiumRegistryAPI.enqueueMixin(false, "mixins.deferred.json", () -> {
            evaluations.incrementAndGet();
            return true;
        });

        assertTrue(FermiumRegistryAPI.isModPresent("jsonpaintings"));
        assertEquals(0, evaluations.get());

        assertTrue(new RebooterCorePlugin().getMixinConfigs().contains("mixins.deferred.json"));
        assertEquals(1, evaluations.get());
    }
}
