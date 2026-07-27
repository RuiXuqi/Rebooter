package fermiumbooter;

import fermiumbooter.rebooter.MixinRegistry;
import fermiumbooter.rebooter.discovery.JarDiscovery;
import fermiumbooter.rebooter.discovery.LegacyConfigRegistrar;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Registration entry point for mods that contribute Mixin configuration files.
 *
 * <p>The {@code late} argument uses one convention across every overload: {@code false} registers an
 * early configuration and {@code true} registers a late configuration. Registrations sharing a name
 * are enabled when at least one supplier returns {@code true}; every supplier is evaluated unless the
 * name was removed with {@link #removeMixin(String)}.
 *
 * <p>Register or remove a configuration before MixinBooter requests the corresponding phase, normally
 * during {@code IFMLLoadingPlugin} initialization. Suppliers are evaluated when that phase is handed
 * off, not when they are registered.
 *
 * @since 1.0.0
 */
@SuppressWarnings({"unused", "DeprecatedIsStillUsed"})
public abstract class FermiumRegistryAPI {
    /**
     * Registers each configuration for unconditional loading in the selected phase.
     *
     * @param late         {@code false} for early loading or {@code true} for late loading
     * @param mixinConfigs Mixin configuration resource names; a {@code null} array or null/blank entries
     *                     are logged and ignored
     * @since 1.0.0
     */
    public static void enqueueMixin(boolean late, String... mixinConfigs) {
        MixinRegistry.enqueue(late, mixinConfigs);
    }

    /**
     * Registers one configuration for unconditional loading in the selected phase.
     *
     * @param late        {@code false} for early loading or {@code true} for late loading
     * @param mixinConfig Mixin configuration resource name; null or blank names are logged and ignored
     * @since 1.0.0
     */
    public static void enqueueMixin(boolean late, String mixinConfig) {
        enqueueMixin(late, mixinConfig, true);
    }

    /**
     * Registers one configuration with a fixed enabled state.
     *
     * @param late        {@code false} for early loading or {@code true} for late loading
     * @param mixinConfig Mixin configuration resource name; null or blank names are logged and ignored
     * @param enabled     whether the configuration is eligible for loading
     * @since 1.0.0
     */
    public static void enqueueMixin(boolean late, String mixinConfig, boolean enabled) {
        MixinRegistry.enqueue(late, mixinConfig, enabled);
    }

    /**
     * Registers one configuration whose enabled state is evaluated when MixinBooter requests that
     * phase.
     *
     * @param late        {@code false} for early loading or {@code true} for late loading
     * @param mixinConfig Mixin configuration resource name; null or blank names are logged and ignored
     * @param enabled     deferred eligibility check; a null supplier is logged and ignored, while a null
     *                    result is logged and treated as {@code false}
     * @since 1.0.0
     */
    public static void enqueueMixin(boolean late, String mixinConfig, Supplier<Boolean> enabled) {
        MixinRegistry.enqueue(late, mixinConfig, enabled);
    }

    /**
     * Rejects a configuration name in both phases without evaluating any of its suppliers.
     *
     * @param mixinConfig Mixin configuration resource name; null or blank names are logged and ignored
     * @since 1.0.0
     */
    public static void removeMixin(String mixinConfig) {
        MixinRegistry.reject(mixinConfig);
    }

    /**
     * Returns whether the supplied mod id is known to Rebooter's discovery index. Null or blank ids return
     * {@code false}. The first valid query may synchronously initialize jar discovery.
     *
     * @param modId mod id to query, case-insensitive
     * @return {@code true} when the id is built in, discovered from a mod, or identified through a configured
     * package mapping
     * @since 1.2.0
     */
    public static boolean isModPresent(String modId) {
        return JarDiscovery.isModPresent(modId);
    }

    /**
     * Registers an already loaded Forge configuration class that uses the legacy 1.2 annotation model.
     * The class must carry {@link net.minecraftforge.common.config.Config @Config}. Public boolean fields
     * annotated with {@link fermiumbooter.annotations.MixinConfig.EarlyMixin @EarlyMixin} or
     * {@link fermiumbooter.annotations.MixinConfig.LateMixin @LateMixin} are registered as Mixin toggles,
     * and {@link fermiumbooter.annotations.MixinConfig.SubInstance @SubInstance} fields are inspected
     * recursively.
     *
     * <p>The method reads the cfg file selected by {@code @Config.name}, falling back to
     * {@code @Config.modid}. A field's current value is used when its cfg property is absent. Legacy
     * annotation names and compatibility rules are read from class bytecode so already compiled legacy
     * configuration classes remain recognizable.
     *
     * <p>Compatibility rules are evaluated and enabled Mixin configurations are enqueued immediately.
     * Call this method before the corresponding Mixin phase is handed off, normally during
     * {@code IFMLLoadingPlugin} initialization.
     *
     * @param <T>            configuration class type
     * @param configClass    Forge configuration class; null or classes without {@code @Config} are logged
     *                       and ignored
     * @param configInstance instance used for non-static fields and nested configuration objects; may be
     *                       {@code null} only when every inspected field is static
     * @since 1.2.0
     * @deprecated since 1.3.0; annotate the class with
     * {@link fermiumbooter.annotations.MixinConfig @MixinConfig} and its boolean toggle fields with
     * {@link fermiumbooter.annotations.MixinConfig.MixinToggle @MixinToggle} for automatic discovery.
     * No explicit registration call is required.
     */
    @Deprecated
    public static <T> void registerAnnotatedMixinConfig(Class<T> configClass, @Nullable T configInstance) {
        LegacyConfigRegistrar.registerForgeConfigClass(configClass, configInstance);
    }
}
