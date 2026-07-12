package fermiumbooter;

import fermiumbooter.rebooter.MixinRegistry;
import fermiumbooter.rebooter.discovery.JarDiscovery;
import fermiumbooter.rebooter.discovery.LegacyConfigRegistrar;

import java.util.function.Supplier;

/**
 * Registration entry point for mods that contribute Mixin configuration files.
 *
 * <p>The {@code late} argument uses one convention across every overload: {@code false} registers an
 * early configuration and {@code true} registers a late configuration. Registrations sharing a name
 * are enabled when at least one supplier returns {@code true}; every supplier is evaluated unless the
 * name was removed with {@link #removeMixin(String)}.
 *
 * @since 1.0.0
 */
@SuppressWarnings({"unused", "DeprecatedIsStillUsed"})
public abstract class FermiumRegistryAPI {
    /**
     * Registers each configuration for unconditional loading in the selected phase.
     *
     * @param late         {@code false} for early loading or {@code true} for late loading
     * @param mixinConfigs Mixin configuration resource names
     */
    public static void enqueueMixin(boolean late, String... mixinConfigs) {
        MixinRegistry.enqueue(late, mixinConfigs);
    }

    /**
     * Registers one configuration for unconditional loading in the selected phase.
     *
     * @param late        {@code false} for early loading or {@code true} for late loading
     * @param mixinConfig Mixin configuration resource name
     */
    public static void enqueueMixin(boolean late, String mixinConfig) {
        enqueueMixin(late, mixinConfig, true);
    }

    /**
     * Registers one configuration with a fixed enabled state.
     *
     * @param late        {@code false} for early loading or {@code true} for late loading
     * @param mixinConfig Mixin configuration resource name
     * @param enabled     whether the configuration is eligible for loading
     */
    public static void enqueueMixin(boolean late, String mixinConfig, boolean enabled) {
        MixinRegistry.enqueue(late, mixinConfig, enabled);
    }

    /**
     * Registers one configuration whose enabled state is evaluated when MixinBooter requests that
     * phase.
     *
     * @param late        {@code false} for early loading or {@code true} for late loading
     * @param mixinConfig Mixin configuration resource name
     * @param enabled     deferred eligibility check
     */
    public static void enqueueMixin(boolean late, String mixinConfig, Supplier<Boolean> enabled) {
        MixinRegistry.enqueue(late, mixinConfig, enabled);
    }

    /**
     * Rejects a configuration name in both phases without evaluating any of its suppliers.
     *
     * @param mixinConfig Mixin configuration resource name
     */
    public static void removeMixin(String mixinConfig) {
        MixinRegistry.reject(mixinConfig);
    }

    /**
     * Returns whether mod discovery knows the supplied mod id. Available in both early and late stage.
     *
     * @param modId mod id to query, case-insensitive
     * @return {@code true} when the mod is available
     */
    public static boolean isModPresent(String modId) {
        return JarDiscovery.isModPresent(modId);
    }

    /**
     * Registers an already loaded Forge configuration class that uses the legacy 1.2 annotation model.
     * Annotation metadata is read from class bytecode so previously compiled configurations retain their
     * historical defaults.
     *
     * @param configClass    Forge configuration class {@link net.minecraftforge.common.config.Config}
     * @param configInstance instance used for non-static configuration fields
     * @since 1.2.0
     * @deprecated 1.3.0. Replaced by automatic scanning, which uses
     * {@link fermiumbooter.annotations.MixinConfig} model instead.
     */
    @Deprecated
    public static void registerAnnotatedMixinConfig(Class<?> configClass, Object configInstance) {
        LegacyConfigRegistrar.registerForgeConfigClass(configClass, configInstance);
    }
}
