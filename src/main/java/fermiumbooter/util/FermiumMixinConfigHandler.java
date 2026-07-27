package fermiumbooter.util;

import fermiumbooter.rebooter.discovery.LegacyConfigRegistrar;

import javax.annotation.Nullable;

/**
 * Compatibility shim preserving a legacy registration entry point from a class that was an internal
 * implementation detail in FermiumBooter. Registration delegates to Rebooter's legacy configuration
 * support.
 *
 * @since 1.2.0
 * @deprecated since 1.3.0; use automatic discovery through
 * {@link fermiumbooter.annotations.MixinConfig @MixinConfig} and
 * {@link fermiumbooter.annotations.MixinConfig.MixinToggle @MixinToggle}.
 */
@Deprecated
public abstract class FermiumMixinConfigHandler {
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
     * @see fermiumbooter.FermiumRegistryAPI#registerAnnotatedMixinConfig(Class, Object)
     * @since 1.2.0
     * @deprecated since 1.3.0; annotate the class with
     * {@link fermiumbooter.annotations.MixinConfig @MixinConfig} and its boolean toggle fields with
     * {@link fermiumbooter.annotations.MixinConfig.MixinToggle @MixinToggle} for automatic discovery.
     * No explicit registration call is required.
     */
    @Deprecated
    public static <T> void registerForgeConfigClass(Class<T> configClass, @Nullable T configInstance) {
        LegacyConfigRegistrar.registerForgeConfigClass(configClass, configInstance);
    }
}
