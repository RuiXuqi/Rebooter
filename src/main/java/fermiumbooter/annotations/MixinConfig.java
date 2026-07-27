package fermiumbooter.annotations;

import java.lang.annotation.*;

/**
 * Declares a cfg file whose Mixin toggles are discovered automatically from this class's bytecode.
 * Boolean fields annotated with {@link MixinToggle @MixinToggle} are read when Rebooter prepares Mixin
 * registration; enabled fields enqueue their declared early and late Mixin resources. The annotated class
 * is not loaded during discovery.
 *
 * <p>The cfg property for a toggle is named by its {@link net.minecraftforge.common.config.Config.Name @Config.Name}
 * value when nonblank, or by its Java field name otherwise. A {@link net.minecraftforge.common.config.Config @Config}
 * annotation is not required for automatic discovery.
 *
 * @since 1.2.0
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MixinConfig {
    /**
     * @return nonblank cfg file name without its {@code .cfg} suffix
     * @since 1.3.0
     */
    String name();

    /**
     * Legacy marker for a nested configuration object inspected by explicit legacy registration.
     * Automatic discovery does not traverse {@code @SubInstance} fields.
     *
     * @see fermiumbooter.FermiumRegistryAPI#registerAnnotatedMixinConfig(Class, Object)
     * @since 1.2.0
     * @deprecated since 1.3.0; automatic discovery scans {@link MixinConfig} classes and their
     * {@link MixinToggle} fields directly.
     */
    @Deprecated
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface SubInstance {
    }

    /**
     * Legacy marker associating a boolean configuration field with an early Mixin resource.
     * It is processed only by explicit legacy registration, not automatic discovery.
     *
     * @since 1.2.0
     * @deprecated since 1.3.0; use {@link MixinToggle#earlyMixin()}.
     */
    @Deprecated
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface EarlyMixin {
        /**
         * @return nonblank Mixin configuration resource name
         * @since 1.2.0
         */
        String name();
    }

    /**
     * Legacy marker associating a boolean configuration field with a late Mixin resource.
     * It is processed only by explicit legacy registration, not automatic discovery.
     *
     * @since 1.2.0
     * @deprecated since 1.3.0; use {@link MixinToggle#lateMixin()}.
     */
    @Deprecated
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface LateMixin {
        /**
         * @return nonblank Mixin configuration resource name
         * @since 1.2.0
         */
        String name();
    }

    /**
     * Defines the early and/or late Mixin resources controlled by an automatically discovered boolean field.
     * At least one resource name must be nonblank for the toggle to enqueue a Mixin configuration.
     *
     * <p>The value stored in the cfg file determines whether the resources are registered. When the property
     * is absent, {@link #defaultValue()} is used; the field's Java initializer is not consulted.
     *
     * @since 1.3.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface MixinToggle {
        /**
         * @return early Mixin resource name, or an empty string when no early resource is registered
         * @since 1.3.0
         */
        String earlyMixin() default "";

        /**
         * @return late Mixin resource name, or an empty string when no late resource is registered
         * @since 1.3.0
         */
        String lateMixin() default "";

        /**
         * @return value used when no matching cfg property exists
         * @since 1.3.0
         */
        boolean defaultValue();
    }

    /**
     * Defines a mod-presence condition for an automatically discovered {@link MixinToggle} field.
     * A failed condition logs a diagnostic; it prevents registration only when {@link #disableMixin()}
     * is {@code true}. Repeat this annotation to apply multiple conditions.
     *
     * @since 1.2.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Repeatable(CompatHandlingContainer.class)
    @interface CompatHandling {
        /**
         * @return mod id queried through case-insensitive discovery
         * @since 1.2.0
         */
        String modid();

        /**
         * Returns the required presence state for the target mod.
         *
         * @return {@code true} when the mod must be present, {@code false} when it must be absent
         * @since 1.2.0
         */
        boolean desired();

        /**
         * @return whether a failed condition prevents the Mixin resources from being registered
         * @since 1.2.0
         */
        boolean disableMixin() default true;

        /**
         * @return whether a failed condition contributes to the in-game compatibility warning count
         * @since 1.3.2
         */
        boolean warnIngame() default true;

        /**
         * @return diagnostic reason included in the log when the condition fails
         * @since 1.2.0
         */
        String reason() default "";
    }

    /**
     * Runtime container used by Java to store repeated {@link CompatHandling} declarations.
     *
     * @since 1.2.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface CompatHandlingContainer {
        /**
         * @return the contained compatibility conditions
         * @since 1.2.0
         */
        CompatHandling[] value();
    }

    /**
     * Runtime container used by Java to store repeated {@link CompatHandling} declarations.
     * Clone of {@link CompatHandlingContainer} in FermiumBooterDepoliticization.
     *
     * @since FermiumBooterDepoliticization 1.2.1
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface CompatHandlings {
        /**
         * @return the contained compatibility conditions
         * @since FermiumBooterDepoliticization 1.2.1
         */
        CompatHandling[] value();
    }
}
