package fermiumbooter.rebooter.discovery;

import com.google.common.annotations.VisibleForTesting;
import fermiumbooter.annotations.MixinConfig;
import fermiumbooter.rebooter.Reference;
import fermiumbooter.rebooter.util.ForgeConfigAccess;
import fermiumbooter.rebooter.util.GameDirectory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class LegacyConfigRegistrar {
    private static int warningCount;

    private LegacyConfigRegistrar() {
    }

    public static void registerForgeConfigClass(Class<?> configClass, Object configInstance) {
        if (configClass == null) {
            Reference.LOGGER.error("Cannot register a null Forge config class");
            return;
        }
        LegacyConfigReader.Metadata metadata = LegacyConfigReader.scan(configClass);
        if (!metadata.isForgeConfig()) {
            Reference.LOGGER.error("Config class {} is missing Forge @Config", configClass.getName());
            return;
        }
        scanFields(
                configClass,
                configInstance,
                metadata.configFileName(),
                metadata);
    }

    @SuppressWarnings("deprecation")
    private static void scanFields(
            Class<?> configClass,
            Object configInstance,
            String configFileName,
            LegacyConfigReader.Metadata metadata) {
        for (Field field : configClass.getFields()) {
            Object owner = Modifier.isStatic(field.getModifiers()) ? null : configInstance;
            if (field.isAnnotationPresent(MixinConfig.SubInstance.class)) {
                try {
                    Object nested = field.get(owner);
                    scanFields(
                            field.getType(),
                            nested,
                            configFileName,
                            LegacyConfigReader.scan(field.getType()));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    Reference.LOGGER.error("Failed to inspect nested config field {}", field, e);
                }
                continue;
            }
            if (field.getType() != boolean.class) continue;
            try {
                String key = metadata.configName(field.getName());
                boolean defaultValue = field.getBoolean(owner);
                boolean enabled = ForgeConfigAccess.findBoolean(
                        GameDirectory.resolve(), configFileName, key, defaultValue);
                MixinConfig.EarlyMixin early = field.getAnnotation(MixinConfig.EarlyMixin.class);
                MixinConfig.LateMixin late = field.getAnnotation(MixinConfig.LateMixin.class);
                warningCount += ToggleRegistrar.register(
                        enabled,
                        early == null ? null : early.name(),
                        late == null ? null : late.name(),
                        metadata.compatibilityRules(field.getName()));
            } catch (ReflectiveOperationException | RuntimeException e) {
                Reference.LOGGER.error("Failed to inspect config field {}", field, e);
            }
        }
    }

    @VisibleForTesting
    static void clearConfigCache() {
        ForgeConfigAccess.clearCompatibilityCache();
    }

    @VisibleForTesting
    static int getWarningCount() {
        return warningCount;
    }

    @VisibleForTesting
    public static void resetForTesting() {
        warningCount = 0;
        ForgeConfigAccess.clearCompatibilityCache();
    }
}
