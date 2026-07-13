package fermiumbooter;

import fermiumbooter.config.FermiumBooterConfig;
import fermiumbooter.rebooter.MixinRegistry;
import fermiumbooter.rebooter.discovery.LegacyConfigRegistrar;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.FMLInjectionData;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;

public final class ForgeTestEnvironment {

    private ForgeTestEnvironment() {
    }

    public static void setGameDirectory(Path gameDirectory) throws ReflectiveOperationException {
        resetRebooterState();
        initializeForgeLaunchArguments();
        System.setProperty("rebooter.gameDir", gameDirectory.toString());
        setInjectedGameDirectory(gameDirectory.toFile());
    }

    public static void clearInjectedGameDirectory() throws ReflectiveOperationException {
        resetRebooterState();
        initializeForgeLaunchArguments();
        setInjectedGameDirectory(null);
    }

    private static void initializeForgeLaunchArguments() {
        if (Launch.blackboard == null) {
            Launch.blackboard = new HashMap<>();
        }
        Launch.blackboard.putIfAbsent("forgeLaunchArgs", new HashMap<String, String>());
    }

    private static void resetRebooterState() {
        FermiumBooterConfig.resetForTesting();
        MixinRegistry.resetForTesting();
        LegacyConfigRegistrar.resetForTesting();
    }

    private static void setInjectedGameDirectory(java.io.File gameDirectory) throws ReflectiveOperationException {
        Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, gameDirectory);
    }
}
