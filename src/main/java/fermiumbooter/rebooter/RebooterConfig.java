package fermiumbooter.rebooter;

import fermiumbooter.config.FermiumBooterConfig;
import fermiumbooter.rebooter.util.ForgeConfigAccess;
import fermiumbooter.rebooter.util.GameDirectory;

import java.util.*;

public final class RebooterConfig {
    private static RebooterConfig instance;
    private static boolean applied;
    private final boolean overrideCompatibilityChecks;
    private final List<String> additions;
    private final List<String> removals;
    // package - modIds
    private final Map<String, Set<String>> modDiscoveryMappings;
    private final Set<String> modDiscoveryTargets;
    private final Set<String> mixinConfigScanAllowlist;

    private RebooterConfig(
            boolean overrideCompatibilityChecks,
            String[] additions,
            String[] removals,
            String[] configuredMappings,
            String[] configuredScanAllowlist
    ) {
        this.overrideCompatibilityChecks = overrideCompatibilityChecks;
        this.additions = Collections.unmodifiableList(Arrays.asList(additions));
        this.removals = Collections.unmodifiableList(Arrays.asList(removals));
        this.modDiscoveryMappings = Collections.unmodifiableMap(parseModDiscoveryMappings(configuredMappings));
        Set<String> targets = new HashSet<>();
        this.modDiscoveryMappings.values().forEach(targets::addAll);
        this.modDiscoveryTargets = Collections.unmodifiableSet(targets);
        this.mixinConfigScanAllowlist = Collections.unmodifiableSet(
                parsePackagePrefixes(configuredScanAllowlist, "Mixin config scan allowlist"));
    }

    public static boolean overrideCompatibilityChecks() {
        return get().overrideCompatibilityChecks;
    }

    public static Map<String, Set<String>> modDiscoveryPackageMappings() {
        return get().modDiscoveryMappings;
    }

    public static Set<String> modDiscoveryTargets() {
        return get().modDiscoveryTargets;
    }

    public static Set<String> mixinConfigScanAllowlist() {
        return get().mixinConfigScanAllowlist;
    }

    private static RebooterConfig get() {
        if (instance == null && ForgeConfigAccess.registerAnnotated(FermiumBooterConfig.class, GameDirectory.resolve())) {
            instance = new RebooterConfig(
                    FermiumBooterConfig.overrideMixinCompatibilityChecks,
                    FermiumBooterConfig.forcedEarlyMixinConfigAdditions,
                    FermiumBooterConfig.forcedEarlyMixinConfigRemovals,
                    FermiumBooterConfig.modDiscoveryPackageMappings,
                    FermiumBooterConfig.mixinConfigScanAllowlist);
        }
        return instance;
    }

    static void apply() {
        if (applied) return;
        RebooterConfig current = get();

        for (String mixin : current.removals) {
            MixinRegistry.reject(mixin);
        }

        MixinRegistry.enqueue(false, current.additions.toArray(new String[0]));
        applied = true;
    }

    private static Map<String, Set<String>> parseModDiscoveryMappings(String[] configuredMappings) {
        Map<String, Set<String>> mappings = new LinkedHashMap<>();
        for (String mapping : configuredMappings) {
            if (mapping == null) {
                invalidMapping(null);
                continue;
            }
            int separator = mapping.indexOf('=');
            if (separator <= 0 || separator != mapping.lastIndexOf('=') || separator == mapping.length() - 1) {
                invalidMapping(mapping);
                continue;
            }
            String packagePrefix = normalizePackage(mapping.substring(0, separator).trim());
            String modId = mapping.substring(separator + 1).trim();
            if (packagePrefix == null || !modId.matches("[A-Za-z0-9_.-]+")) {
                invalidMapping(mapping);
                continue;
            }
            mappings.computeIfAbsent(packagePrefix, ignored -> new HashSet<>()).add(modId);
        }
        return mappings;
    }

    private static Set<String> parsePackagePrefixes(String[] configuredPrefixes, String description) {
        Set<String> prefixes = new LinkedHashSet<>();
        for (String configuredPrefix : configuredPrefixes) {
            String prefix = configuredPrefix == null ? null : normalizePackage(configuredPrefix.trim());
            if (prefix == null) {
                Reference.LOGGER.warn("Ignoring invalid {} entry '{}'", description, configuredPrefix);
            } else {
                prefixes.add(prefix);
            }
        }
        return prefixes;
    }

    private static String normalizePackage(String packageName) {
        String normalized = packageName.replace('\\', '/').replace('.', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty() || normalized.contains("//") || !normalized.matches("[A-Za-z0-9_/]+")) {
            return null;
        }
        return normalized + "/";
    }

    private static void invalidMapping(String mapping) {
        Reference.LOGGER.warn(
                "Ignoring invalid mod discovery package mapping '{}'; expected package.name=modid",
                mapping);
    }
}
