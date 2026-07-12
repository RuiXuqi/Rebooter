package fermiumbooter.rebooter.discovery;

import fermiumbooter.rebooter.MixinRegistry;
import fermiumbooter.rebooter.RebooterConfig;
import fermiumbooter.rebooter.Reference;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
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
        if (!RebooterConfig.overrideCompatibilityChecks()) {
            List<String> failedRules = new ArrayList<>();
            for (CompatRule rule : compatibilityRules) {
                boolean present = JarDiscovery.isModPresent(rule.modid());
                if (rule.desired() != present) {
                    if (rule.warnIngame()) warnings++;
                    if (rule.disableMixin()) disabled = true;
                    if (StringUtils.isNotBlank(rule.reason()))
                        failedRules.add(String.format("%s mod '%s' is %s (reason=%s; disable=%s)",
                                rule.desired() ? "Desired" : "Undesired",
                                rule.modid(),
                                present ? "present" : "absent",
                                rule.reason(),
                                rule.disableMixin()));
                    else failedRules.add(String.format("%s mod '%s' is %s (disable=%s)",
                            rule.desired() ? "Desired" : "Undesired",
                            rule.modid(),
                            present ? "present" : "absent",
                            rule.disableMixin()));
                }
            }
            if (!failedRules.isEmpty()) {
                Reference.LOGGER.warn(
                        "Mixin config {} was {}: {}",
                        describeConfigs(earlyConfig, lateConfig, hasEarly, hasLate),
                        disabled ? "disabled" : "queued",
                        String.join("; ", failedRules));
            }
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
        if (hasLate) return String.format("late '%s", lateConfig);
        return "none";
    }
}
