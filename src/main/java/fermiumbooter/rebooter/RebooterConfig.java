package fermiumbooter.rebooter;

import com.google.common.annotations.VisibleForTesting;
import net.minecraftforge.common.config.Config;

@Config(modid = Reference.MOD_ID, name = Reference.INTERNAL_ID, category = "")
public final class RebooterConfig {
    private static final String PREFIX = Reference.MOD_ID + ".config.";
    private static final String[] DEFAULT_PACKAGE_MAPPINGS = new String[]{
            "git.jbredwards.jsonpaintings=jsonpaintings",
            "net.jan.moddirector=moddirector",
            "gg.essential=essential"
    };

    @Config.LangKey(PREFIX + "network")
    @Config.Comment("Network version override.")
    public static final Network network = new Network();

    @Config.LangKey(PREFIX + "client")
    @Config.Comment("Client-only behavior.")
    public static final Client client = new Client();

    @Config.RequiresMcRestart
    @Config.LangKey(PREFIX + "mixins")
    @Config.Comment("Mixin config registration overrides.")
    public static final Mixins mixins = new Mixins();

    @Config.RequiresMcRestart
    @Config.LangKey(PREFIX + "discovery")
    @Config.Comment("Mod discovery fallbacks.")
    public static final Discovery discovery = new Discovery();

    @Config.LangKey(PREFIX + "cache")
    @Config.Comment("JAR discovery cache.")
    public static final Cache cache = new Cache();

    @Config.LangKey(PREFIX + "diagnostics")
    @Config.Comment("Diagnostic logging.")
    public static final Diagnostics diagnostics = new Diagnostics();

    public static final class Network {
        @Config.LangKey(PREFIX + "network.versionOverride")
        @Config.Comment({
                "Uses the configured network version instead of compat version.",
                "This changes the version exposed through the mod container, not local mod compat checks."
        })
        public boolean versionOverride = false;

        @Config.LangKey(PREFIX + "network.version")
        @Config.Comment("Version exposed through the mod container when the network version override is enabled.")
        public String version = Reference.COMPAT_VERSION;
    }

    public static final class Client {
        @Config.LangKey(PREFIX + "client.hideWarningToast")
        @Config.Comment("Prevents the in-game toast that reports Mixin config compatibility warnings.")
        public boolean hideWarningToast = true;
    }

    public static final class Mixins {
        @Config.LangKey(PREFIX + "mixins.ignoreCompatChecks")
        @Config.Comment({
                "Skips CompatHandling requirements when an annotated Mixin toggle is enabled.",
                "Failed compatibility checks will neither disable the Mixin nor produce warnings."
        })
        public boolean ignoreCompatChecks = false;

        @Config.LangKey(PREFIX + "mixins.forceLoadConfigs")
        @Config.Comment({
                "Mixin configuration resource names to register unconditionally in the early loading phase.",
                "A name also listed in removals remains disabled."
        })
        public String[] forceLoadConfigs = new String[]{};

        @Config.LangKey(PREFIX + "mixins.blockConfigs")
        @Config.Comment({
                "Mixin configuration resource names to reject from both early and late loading.",
                "Removal wins over forced additions, annotations, and API registrations with the same name."
        })
        public String[] blockConfigs = new String[]{};
    }

    public static final class Discovery {
        @Config.LangKey(PREFIX + "discovery.overridePackageMappings")
        @Config.Comment({
                "Uses the configured package mappings instead of the built-in defaults.",
                "The configured list is ignored while this stays disabled."
        })
        public boolean overridePackageMappings = false;

        @Config.LangKey(PREFIX + "discovery.packageMappings")
        @Config.Comment({
                "Fallback package prefixes for mod IDs that discovery does not report.",
                "A mod ID is considered present when any scanned JAR entry starts with its mapped prefix.",
                "Format: package.name=modid. Dotted and slash-separated package names are accepted.",
                "Mod IDs preserve their configured case; API and compatibility queries ignore case.",
                "Applies only when the package mappings override is on."
        })
        public String[] packageMappings = DEFAULT_PACKAGE_MAPPINGS.clone();

        @Config.LangKey(PREFIX + "discovery.classScanAllowlist")
        @Config.Comment({
                "Package prefixes that remain eligible for @MixinConfig and @Mod scanning when normally filtered.",
                "Use this only for discovery classes placed under bundled library packages.",
                "Dotted and slash-separated package names are accepted."
        })
        public String[] classScanAllowlist = new String[]{};
    }

    public static final class Cache {
        @Config.LangKey(PREFIX + "cache.enabled")
        @Config.Comment({
                "Caches JAR discovery results between launches.",
                "Disable to rescan every candidate JAR on every launch."
        })
        public boolean enabled = true;

        @Config.LangKey(PREFIX + "cache.verifyMode")
        @Config.Comment({
                "Cache verification mode: always or auto.",
                "always re-fingerprints every cached JAR; auto trusts unchanged file stamps."
        })
        public String verifyMode = "always";
    }

    public static final class Diagnostics {
        @Config.LangKey(PREFIX + "diagnostics.logStats")
        @Config.Comment("Logs JAR discovery timing statistics.")
        public boolean logStats = false;
    }

    private RebooterConfig() {
    }

    public static String[] effectivePackageMappings() {
        return discovery.overridePackageMappings
                ? discovery.packageMappings
                : DEFAULT_PACKAGE_MAPPINGS.clone();
    }

    @VisibleForTesting
    public static void resetForTesting() {
        network.versionOverride = false;
        network.version = Reference.COMPAT_VERSION;
        client.hideWarningToast = true;
        mixins.ignoreCompatChecks = false;
        mixins.forceLoadConfigs = new String[]{};
        mixins.blockConfigs = new String[]{};
        discovery.overridePackageMappings = false;
        discovery.packageMappings = DEFAULT_PACKAGE_MAPPINGS.clone();
        discovery.classScanAllowlist = new String[]{};
        cache.enabled = true;
        cache.verifyMode = "always";
        diagnostics.logStats = false;
    }
}
