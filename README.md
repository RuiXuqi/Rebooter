# Rebooter

An elegant drop-in replacement for FermiumBooter, featuring broad API compatibility, cached annotation scanning, and
complete freedom, backed by MixinBooter.

## Usage

For users, remove FermiumBooter, then install Rebooter with
[MixinBooter](https://github.com/CleanroomMC/MixinBooter) 10.x or higher. FermiumBooter-dependent mods should then
function normally. Make sure the leading backtick in the file name is present; otherwise, a `ClassNotFoundException` may
be thrown. When using [Cleanroom Loader](https://github.com/CleanroomMC/Cleanroom), MixinBooter is not needed.

For developers, we recommend making your mod depend directly on
[MixinBooter](https://github.com/CleanroomMC/MixinBooter) and
[ConfigAnyTime](https://github.com/CleanroomMC/ConfigAnytime) to load and control mixins. This approach provides more
advanced mixin management and better compatibility. Add Rebooter as a dependency only when one of your dependencies
requires FermiumBooter.

## Compatibility

The current compatibility target is FermiumBooter **1.4.1**.

Rebooter aims to provide API and ABI compatibility through 1.4.1, including APIs introduced in 1.2.0 and removed in
1.3.0.

The table lists the supported APIs introduced by each FermiumBooter release; releases with no API additions are omitted.

| Version | APIs introduced                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
|:-------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|  1.0.0  | `FermiumRegistryAPI.enqueueMixin(boolean, String...)`<br>`FermiumRegistryAPI.enqueueMixin(boolean, String)`<br>`FermiumRegistryAPI.enqueueMixin(boolean, String, boolean)`<br>`FermiumRegistryAPI.enqueueMixin(boolean, String, Supplier<Boolean>)`<br>`FermiumRegistryAPI.removeMixin(String)`                                                                                                                                                                                                                                                                                                                                                                                                                |
|  1.2.0  | `FermiumRegistryAPI.isModPresent(String)`<br><code>FermiumRegistryAPI.registerAnnotatedMixinConfig(Class&lt;?&gt;, Object)</code> (removed in 1.3.0)<br>`FermiumEarlyModIDSearcher.isModPresent(String)` (removed in 1.3.0)<br>`FermiumMixinConfigHandler.registerForgeConfigClass(Class<?>, Object)` (removed in 1.3.0)<br>`MixinConfig.SubInstance` (removed in 1.3.0)<br>`MixinConfig.EarlyMixin.name()` (removed in 1.3.0)<br>`MixinConfig.LateMixin.name()` (removed in 1.3.0)<br>`MixinConfig.CompatHandling.modid()`<br>`MixinConfig.CompatHandling.desired()`<br>`MixinConfig.CompatHandling.disableMixin()`<br>`MixinConfig.CompatHandling.reason()`<br>`MixinConfig.CompatHandlingContainer.value()` |
|  1.3.0  | `FermiumJarScanner.isModPresent(String)`<br>`MixinConfig.name()`<br>`MixinConfig.MixinToggle.earlyMixin()`<br>`MixinConfig.MixinToggle.lateMixin()`<br>`MixinConfig.MixinToggle.defaultValue()`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
|  1.3.2  | `MixinConfig.CompatHandling.warnIngame()`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |

Rebooter also supports a configurable network version. To connect to existing FermiumBooter servers, configure
`Enable Custom Network Version` and `Custom Network Version` in Rebooter's `fermiumbooter.cfg`. Although FermiumBooter
contains no network components, Forge applies its default `@Mod` version check during connection.
[FermiumBooter.java#L9](https://github.com/FermiumModding/FermiumBooter/blob/v1.4.1/src/main/java/fermiumbooter/FermiumBooter.java#L9)

## Annotation scanning

FermiumBooter introduced `@MixinConfig` in 1.2.0, providing a way for developers to manage Mixin configuration
resources. However, in 1.3.0, the registry was replaced by a full ASM class scan on every launch. In an RLCraft Dregora
instance with 230 mods, this increased launch time by about 2.0 seconds.

Rebooter efficiently and accurately supports both approaches using optimized ASM scanning and a cache. The cache is
rebuilt automatically when mod changes are detected, preserving its validity.

## Liberty

Rebooter is implemented from scratch and uses FermiumBooter only as a reference for API compatibility, providing an
independent libre alternative for everyone.

The mod code under `src/` is released under **The Unlicense**, permitting unrestricted reuse. The project template is
[RuiXuqi/ForgeDevEnv](https://github.com/RuiXuqi/ForgeDevEnv), adapted from
[CleanroomMC/ForgeDevEnv](https://github.com/CleanroomMC/ForgeDevEnv), and is licensed under **MIT License**.
