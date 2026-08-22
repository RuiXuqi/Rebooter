package fermiumbooter.rebooter.discovery;

import fermiumbooter.rebooter.MixinRegistry;
import fermiumbooter.rebooter.RebooterConfig;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

final class ToggleRegistrar {

    private ToggleRegistrar() {
    }

    static int register(boolean enabled, String earlyConfig, String lateConfig,
                        List<CompatRule> compatibilityRules) {
        if (!enabled) return 0;
        boolean hasEarly = StringUtils.isNotBlank(earlyConfig);
        boolean hasLate = StringUtils.isNotBlank(lateConfig);
        int warnings = 0;
        boolean disabled = false;
        if (!compatibilityRules.isEmpty() && !RebooterConfig.overrideCompatibilityChecks()) {
            CompatEvaluator.Outcome outcome = CompatEvaluator.evaluate(
                    JarDiscovery.modIndex(),
                    compatibilityRules,
                    describeConfigs(earlyConfig, lateConfig, hasEarly, hasLate));
            disabled = outcome.disabled();
            warnings = outcome.warnings();
        }
        if (!disabled) {
            if (hasEarly) MixinRegistry.enqueue(false, earlyConfig, true);
            if (hasLate) MixinRegistry.enqueue(true, lateConfig, true);
        }
        return warnings;
    }

    private static String describeConfigs(String earlyConfig, String lateConfig,
                                          boolean hasEarly, boolean hasLate) {
        if (hasEarly && hasLate) return String.format("for early '%s' and late '%s'", earlyConfig, lateConfig);
        if (hasEarly) return String.format("early '%s'", earlyConfig);
        if (hasLate) return String.format("late '%s'", lateConfig);
        return "none";
    }
}
