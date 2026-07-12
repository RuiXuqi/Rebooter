package fermiumbooter.annotations;

import java.lang.annotation.*;

/**
 * Marks a Forge configuration class {@link net.minecraftforge.common.config.Config}
 * for automatic Mixin registration under the named cfg file.
 *
 * @since 1.2.0
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MixinConfig {
    /**
     * @return the cfg file name without its {@code .cfg} suffix
     * @since 1.3.0
     */
    String name();

    /**
     * Marks a field whose value is a nested configuration object inspected by registration.
     *
     * @see fermiumbooter.FermiumRegistryAPI#registerAnnotatedMixinConfig(Class, Object)
     * @deprecated 1.3.0. Replaced by automatic scanning, which uses
     * {@link fermiumbooter.annotations.MixinConfig} model instead.
     */
    @Deprecated
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface SubInstance {
    }

    /**
     * Associates a boolean configuration field with an early Mixin configuration.
     *
     * @deprecated 1.3.0. Use {@link MixinToggle#earlyMixin()} instead.
     */
    @Deprecated
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface EarlyMixin {
        /**
         * @return the Mixin configuration resource name
         */
        String name();
    }

    /**
     * Associates a boolean configuration field with a late Mixin configuration.
     *
     * @deprecated 1.3.0. Use {@link MixinToggle#lateMixin()} instead.
     */
    @Deprecated
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface LateMixin {
        /**
         * @return the Mixin configuration resource name
         */
        String name();
    }

    /**
     * Defines the early and/or late Mixin resources controlled by an automatically scanned field.
     *
     * @since 1.3.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface MixinToggle {
        /**
         * @return the early Mixin resource name, or an empty string when none is registered
         */
        String earlyMixin() default "";

        /**
         * @return the late Mixin resource name, or an empty string when none is registered
         */
        String lateMixin() default "";

        /**
         * @return the field value used when no matching cfg property exists
         */
        boolean defaultValue();
    }

    /**
     * Defines a mod-presence condition that may disable its annotated Mixin toggle.
     * Repeat this annotation on a field to require multiple compatibility conditions.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Repeatable(CompatHandlingContainer.class)
    @interface CompatHandling {
        /**
         * @return the mod id queried through case-insensitive mod discovery
         */
        String modid();

        /**
         * Returns whether the mod is expected to be present.
         *
         * @return {@code true} when the mod is required, {@code false} when it is incompatible
         */
        boolean desired();

        /**
         * @return whether a failed condition disables the Mixin toggle
         */
        boolean disableMixin() default true;

        /**
         * @return whether a failed condition contributes a compatibility warning
         * @since 1.3.2
         */
        boolean warnIngame() default true;

        /**
         * @return the diagnostic reason logged when the condition fails
         */
        String reason() default "";
    }

    /**
     * Runtime container used by Java to store repeated {@link CompatHandling} declarations.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface CompatHandlingContainer {
        /**
         * @return the contained compatibility conditions
         */
        CompatHandling[] value();
    }

    /**
     * Runtime container used by Java to store repeated {@link CompatHandling} declarations.
     * Clone of {@link CompatHandlingContainer} in FermiumBooterDepoliticization.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface CompatHandlings {
        /**
         * @return the contained compatibility conditions
         */
        CompatHandling[] value();
    }
}
