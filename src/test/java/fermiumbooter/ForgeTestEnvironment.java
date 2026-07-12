package fermiumbooter;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.FMLInjectionData;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;

public final class ForgeTestEnvironment {

    private ForgeTestEnvironment() {
    }

    public static void setGameDirectory(Path gameDirectory) throws ReflectiveOperationException {
        System.setProperty("rebooter.gameDir", gameDirectory.toString());
        setInjectedGameDirectory(gameDirectory.toFile());
    }

    public static void clearInjectedGameDirectory() throws ReflectiveOperationException {
        setInjectedGameDirectory(null);
    }

    private static void setInjectedGameDirectory(java.io.File gameDirectory) throws ReflectiveOperationException {
        if (Launch.blackboard == null) {
            Launch.blackboard = new HashMap<>();
        }
        Launch.blackboard.putIfAbsent("forgeLaunchArgs", new HashMap<String, String>());
        Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, gameDirectory);
    }
}
