package fermiumbooter.rebooter;

import com.google.common.annotations.VisibleForTesting;
import fermiumbooter.rebooter.discovery.JarDiscovery;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Supplier;

public final class MixinRegistry {
    private static HashMap<String, List<Supplier<Boolean>>> earlyMixins = new LinkedHashMap<>();
    private static HashMap<String, List<Supplier<Boolean>>> lateMixins = new LinkedHashMap<>();
    private static List<String> rejectedMixins = new ArrayList<>();
    private static boolean prepared;
    private static boolean earlyHandedOff;
    private static boolean lateHandedOff;

    private MixinRegistry() {
    }

    @VisibleForTesting
    public static synchronized void resetForTesting() {
        earlyMixins = new LinkedHashMap<>();
        lateMixins = new LinkedHashMap<>();
        rejectedMixins = new ArrayList<>();
        prepared = false;
        earlyHandedOff = false;
        lateHandedOff = false;
        RebooterConfig.resetForTesting();
        JarDiscovery.resetForTesting();
    }

    public static void enqueue(boolean late, String... mixinConfigs) {
        if (mixinConfigs == null) {
            Reference.LOGGER.error("Cannot enqueue a null mixin config array");
            return;
        }
        for (String mixinConfig : mixinConfigs) {
            enqueue(late, mixinConfig, true);
        }
    }

    public static void enqueue(boolean late, String mixinConfig, boolean enabled) {
        enqueue(late, mixinConfig, () -> enabled);
    }

    public static void enqueue(boolean late, String mixinConfig, Supplier<Boolean> enabled) {
        HashMap<String, List<Supplier<Boolean>>> mixins = late ? lateMixins : earlyMixins;
        String stage = late ? "late" : "early";
        if (invalidName(mixinConfig) || enabled == null || mixins == null) {
            if (enabled == null) Reference.LOGGER.error(
                    "Cannot enqueue {} mixin config '{}' with a null supplier",
                    stage, mixinConfig);
            else if (mixins == null) Reference.LOGGER.error(
                    "Cannot enqueue {} mixin config '{}' after registry clear",
                    stage, mixinConfig);
            return;
        }
        mixins.computeIfAbsent(mixinConfig, ignored -> new ArrayList<>()).add(enabled);
    }

    public static void reject(String mixinConfig) {
        if (invalidName(mixinConfig) || rejectedMixins == null) {
            if (rejectedMixins == null) Reference.LOGGER.error(
                    "Cannot reject mixin config '{}' after registry clear", mixinConfig);
            return;
        }
        rejectedMixins.add(mixinConfig);
    }

    private static boolean invalidName(String mixinConfig) {
        if (StringUtils.isBlank(mixinConfig)) {
            Reference.LOGGER.error("Cannot use a null or blank mixin config name");
            return true;
        }
        return false;
    }

    // Internal lifecycle

    static synchronized List<String> handoffEarly() {
        prepare();
        if (earlyHandedOff) return Collections.emptyList();
        earlyHandedOff = true;
        List<String> evaluated = evaluate(earlyMixins, "early");
        Reference.LOGGER.info("Hand off {} early mixin configs", evaluated.size());
        return evaluated;
    }

    static synchronized List<String> handoffLate() {
        prepare();
        if (lateHandedOff) return Collections.emptyList();
        lateHandedOff = true;
        List<String> evaluated = evaluate(lateMixins, "late");
        clear();
        Reference.LOGGER.info("Hand off {} late mixin configs", evaluated.size());
        return evaluated;
    }

    private static void prepare() {
        if (prepared) return;
        RebooterConfig.apply();
        JarDiscovery.registerConfigs();
        prepared = true;
    }

    private static void clear() {
        earlyMixins = null;
        lateMixins = null;
        rejectedMixins = null;
        JarDiscovery.clear();
    }

    private static List<String> evaluate(HashMap<String, List<Supplier<Boolean>>> mixins, String stage) {
        List<String> accepted = new ArrayList<>();
        if (mixins == null) return accepted;
        for (Map.Entry<String, List<Supplier<Boolean>>> entry : mixins.entrySet()) {
            if (rejectedMixins != null && rejectedMixins.contains(entry.getKey())) {
                continue;
            }
            boolean enabled = false;
            // Run every supplier
            for (Supplier<Boolean> supplier : entry.getValue()) {
                Boolean result = supplier.get();
                if (result == null) {
                    Reference.LOGGER.error("Supplier for {} mixin config '{}' returned null", stage, entry.getKey());
                } else {
                    enabled |= result;
                }
            }
            if (enabled) accepted.add(entry.getKey());
        }
        return accepted;
    }
}
