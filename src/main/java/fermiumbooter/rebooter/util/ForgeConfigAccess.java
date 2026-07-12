package fermiumbooter.rebooter.util;

import fermiumbooter.rebooter.Reference;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Configuration;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;

@SuppressWarnings("SameParameterValue")
public final class ForgeConfigAccess {
    // Compatibility reads intentionally stay outside Forge's global cache: they must not register or save
    // third-party configuration classes during the early bytecode-only discovery phase.
    private static final Map<File, Configuration> COMPATIBILITY_CONFIGS = new HashMap<>();
    @Nullable
    private static final MethodHandle CLEANROOM_REGISTER = findCleanroomConfigMethod("register");
    @Nullable
    private static Method forgeSync;
    @Nullable
    private static Field configurations;
    @Nullable
    private static Field modConfigClasses;
    @Nullable
    private static Field configurationFile;
    @Nullable
    private static Field configurationFileName;

    private ForgeConfigAccess() {
    }

    public static synchronized boolean registerAnnotated(Class<?> configClass, File forgeGameDirectory) {
        Config annotation = getAnnotation(configClass);
        if (annotation == null) {
            return false;
        }

        if (CLEANROOM_REGISTER != null) {
            return invokeCleanroom(CLEANROOM_REGISTER, configClass, "register");
        }

        return registerWithForge(configClass, annotation, forgeGameDirectory);
    }

    public static synchronized void clearCompatibilityCache() {
        COMPATIBILITY_CONFIGS.clear();
    }

    // Cleanroom

    @Nullable
    private static MethodHandle findCleanroomConfigMethod(String name) {
        try {
            Class.forName(
                    ConfigManager.class.getName(),
                    true,
                    ConfigManager.class.getClassLoader());
            MethodHandles.Lookup lookup = MethodHandles.publicLookup().in(ConfigManager.class);
            return lookup.findStatic(
                    ConfigManager.class,
                    name,
                    MethodType.methodType(void.class, Class.class));
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean invokeCleanroom(MethodHandle method, Class<?> configClass, String operation) {
        try {
            method.invokeExact(configClass);
            return true;
        } catch (Throwable e) {
            Reference.LOGGER.error(
                    "Cleanroom failed to {} config '{}'",
                    operation,
                    configClass.getName(),
                    e);
            return false;
        }
    }

    // Forge

    private static boolean registerWithForge(Class<?> configClass, Config annotation, File gameDirectory) {
        String configName = annotation.name().isEmpty() ? annotation.modid() : annotation.name();
        File configFile = configFile(gameDirectory, configName);
        try {
            //noinspection unchecked
            Map<String, Configuration> loadedConfigurations = (Map<String, Configuration>) configurations().get(null);
            Configuration config = loadedConfigurations.get(configFile.getAbsolutePath());
            if (config == null) {
                config = open(configFile);
                loadedConfigurations.put(configFile.getAbsolutePath(), config);
            }

            //noinspection unchecked
            Map<String, Set<Class<?>>> configClasses = (Map<String, Set<Class<?>>>) modConfigClasses().get(null);
            configClasses.computeIfAbsent(annotation.modid(), ignored -> new HashSet<>()).add(configClass);
            forgeSync().invoke(
                    null,
                    config,
                    configClass,
                    annotation.modid(),
                    annotation.category(),
                    true,
                    null);
            config.save();
            return true;
        } catch (InvocationTargetException e) {
            Reference.LOGGER.error("Failed to synchronize config '{}'", configFile, e.getCause());
        } catch (ReflectiveOperationException | RuntimeException e) {
            Reference.LOGGER.error("Failed to load config '{}'", configFile, e);
        }
        return false;
    }

    private static Method forgeSync() {
        if (forgeSync == null) {
            forgeSync = declaredMethod(
                    ConfigManager.class,
                    "sync",
                    Configuration.class,
                    Class.class,
                    String.class,
                    String.class,
                    boolean.class,
                    Object.class);
        }
        return forgeSync;
    }

    private static Field configurations() {
        if (configurations == null) {
            configurations = declaredField(ConfigManager.class, "CONFIGS");
        }
        return configurations;
    }

    private static Field modConfigClasses() {
        if (modConfigClasses == null) {
            modConfigClasses = declaredField(ConfigManager.class, "MOD_CONFIG_CLASSES");
        }
        return modConfigClasses;
    }

    private static Field configurationFile() {
        if (configurationFile == null) {
            configurationFile = declaredField(Configuration.class, "file");
        }
        return configurationFile;
    }

    private static Field configurationFileName() {
        if (configurationFileName == null) {
            configurationFileName = declaredField(Configuration.class, "fileName");
        }
        return configurationFileName;
    }

    private static Method declaredMethod(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Field declaredField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Nullable
    private static Config getAnnotation(Class<?> configClass) {
        Config annotation = configClass.getAnnotation(Config.class);
        if (annotation == null) {
            Reference.LOGGER.error("Config class '{}' is missing @Config", configClass.getName());
        }
        return annotation;
    }

    public static boolean findBoolean(File gameDirectory, String configName, String key, boolean defaultValue) {
        try {
            Configuration config = compatibilityConfiguration(gameDirectory, configName);
            boolean found = false;
            boolean enabled = false;
            for (String category : config.getCategoryNames()) {
                if (config.hasKey(category, key)) {
                    found = true;
                    enabled |= config.get(category, key, defaultValue).getBoolean(defaultValue);
                }
            }
            return found ? enabled : defaultValue;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Reference.LOGGER.error("Failed to find '{}' in early config '{}'", key, configName, e);
        }
        return defaultValue;
    }

    private static synchronized Configuration compatibilityConfiguration(File gameDirectory, String configName)
            throws IllegalAccessException {
        File configFile = configFile(gameDirectory, configName);
        Configuration config = COMPATIBILITY_CONFIGS.get(configFile);
        if (config == null) {
            config = open(configFile);
            COMPATIBILITY_CONFIGS.put(configFile, config);
        }
        return config;
    }

    private static File configFile(File gameDirectory, String configName) {
        return new File(new File(gameDirectory, "config"), configName + ".cfg").getAbsoluteFile();
    }

    private static Configuration open(File configFile) throws IllegalAccessException {
        Configuration config = initialize(configFile);
        try {
            config.load();
            return config;
        } catch (Throwable e) {
            File backup = new File(configFile.getAbsolutePath() + "_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".errored");
            Reference.LOGGER.error("Failed to parse early config '{}'. Renaming it to '{}' and generating defaults.",
                    configFile, backup, e);
            //noinspection ResultOfMethodCallIgnored
            configFile.renameTo(backup);
            config = initialize(configFile);
            config.load();
            return config;
        }
    }

    private static Configuration initialize(File configFile) throws IllegalAccessException {
        Configuration configuration = new Configuration();
        configurationFile().set(configuration, configFile);
        configurationFileName().set(configuration, configFile.getName());
        return configuration;
    }
}
