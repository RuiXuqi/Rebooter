package fermiumbooter;

import fermiumbooter.rebooter.RebooterCorePlugin;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ForcedConfigHandlerTest {

    @TempDir
    Path gameDirectory;

    @Test
    void forcedConfigLoadsBeforeFmlInjectionDataAndRemovalWins() throws Exception {
        Path configDirectory = Files.createDirectories(this.gameDirectory.resolve("config"));
        Files.write(
                configDirectory.resolve("fermiumbooter.cfg"),
                ("general {\n"
                        + "    S:\"Forced Early Mixin Config Additions\" <\n"
                        + "        mixins.added.json\n"
                        + "        mixins.collision.json\n"
                        + "    >\n"
                        + "    S:\"Forced Early Mixin Config Removals\" <\n"
                        + "        mixins.collision.json\n"
                        + "    >\n"
                        + "}\n")
                        .getBytes(StandardCharsets.UTF_8));
        System.setProperty("rebooter.gameDir", this.gameDirectory.toString());
        ForgeTestEnvironment.clearInjectedGameDirectory();
        assertNull(FMLInjectionData.data()[6]);

        List<String> earlyMixins = new RebooterCorePlugin().getMixinConfigs();
        assertEquals(Arrays.asList("mixins.fermiumbooter.init.json", "mixins.added.json"), earlyMixins);
        assertFalse(earlyMixins.contains("mixins.collision.json"));
    }
}
