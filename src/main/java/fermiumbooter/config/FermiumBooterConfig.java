package fermiumbooter.config;

import fermiumbooter.rebooter.Rebooter;
import fermiumbooter.rebooter.Reference;
import net.minecraftforge.common.config.Config;

@Config(modid = Reference.MOD_ID)
public final class FermiumBooterConfig {
    private static final String PREFIX = Reference.MOD_ID + ".config.";

    @Config.LangKey(PREFIX + "enableCustomNetworkVersion")
    @Config.Name("Enable Custom Network Version")
    @Config.Comment({
            "Uses the configured network version instead of compat version.",
            "This changes the version exposed through the mod container, not mod compat checks."
    })
    public static boolean enableCustomNetworkVersion = false;

    @Config.LangKey(PREFIX + "customNetworkVersion")
    @Config.Name("Custom Network Version")
    @Config.Comment("Version exposed through the mod container when the custom network version is enabled.")
    public static String customNetworkVersion = Rebooter.COMPAT_VERSION;

    @Config.LangKey(PREFIX + "overrideMixinCompatibilityChecks")
    @Config.RequiresMcRestart
    @Config.Name("Override Mixin Config Compatibility Checks")
    @Config.Comment({
            "Skips CompatHandling requirements when an annotated Mixin toggle is enabled.",
            "Failed compatibility checks will neither disable the Mixin nor produce warnings."
    })
    public static boolean overrideMixinCompatibilityChecks = false;

    @Config.LangKey(PREFIX + "forcedEarlyMixinConfigAdditions")
    @Config.RequiresMcRestart
    @Config.Name("Forced Early Mixin Config Additions")
    @Config.Comment({
            "Mixin configuration resource names to register unconditionally in the early loading phase.",
            "A name also listed in removals remains disabled."
    })
    public static String[] forcedEarlyMixinConfigAdditions = new String[]{};

    @Config.LangKey(PREFIX + "forcedEarlyMixinConfigRemovals")
    @Config.RequiresMcRestart
    @Config.Name("Forced Early Mixin Config Removals")
    @Config.Comment({
            "Mixin configuration resource names to reject from both early and late loading.",
            "Removal wins over forced additions, annotations, and API registrations with the same name."
    })
    public static String[] forcedEarlyMixinConfigRemovals = new String[]{};

    @Config.LangKey(PREFIX + "modDiscoveryPackageMappings")
    @Config.RequiresMcRestart
    @Config.Name("Mod Discovery Package Mappings")
    @Config.Comment({
            "Fallback package prefixes for mod IDs that discovery does not report.",
            "A mod ID is considered present when any scanned JAR entry starts with its mapped prefix.",
            "Format: package.name=modid. Dotted and slash-separated package names are accepted.",
            "Mod IDs preserve their configured case; API and compatibility queries ignore case."
    })
    public static String[] modDiscoveryPackageMappings = new String[]{
            "git.jbredwards.jsonpaintings=jsonpaintings",
            "net.jan.moddirector=moddirector"
    };

    @Config.LangKey(PREFIX + "mixinConfigScanAllowlist")
    @Config.RequiresMcRestart
    @Config.Name("Mixin Config Scan Allowlist")
    @Config.Comment({
            "Package prefixes that remain eligible for @MixinConfig scanning when normally filtered.",
            "Use this only for configuration classes placed under bundled library packages.",
            "Dotted and slash-separated package names are accepted."
    })
    public static String[] mixinConfigScanAllowlist = new String[]{};
}
