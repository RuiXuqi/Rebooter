package fermiumbooter.rebooter.discovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DiscoveredConfig {
    private final String configName;
    private final List<Toggle> toggles;

    DiscoveredConfig(String configName, List<Toggle> toggles) {
        this.configName = configName;
        this.toggles = immutableCopy(toggles);
    }

    String configName() {
        return this.configName;
    }

    List<Toggle> toggles() {
        return this.toggles;
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    static final class Toggle {
        private final String configFieldName;
        private final String earlyMixinName;
        private final String lateMixinName;
        private final boolean defaultValue;
        private final List<CompatRule> compatibilityRules;

        Toggle(
                String configFieldName,
                String earlyMixinName,
                String lateMixinName,
                boolean defaultValue,
                List<CompatRule> compatibilityRules) {
            this.configFieldName = configFieldName;
            this.earlyMixinName = earlyMixinName;
            this.lateMixinName = lateMixinName;
            this.defaultValue = defaultValue;
            this.compatibilityRules = immutableCopy(compatibilityRules);
        }

        String configFieldName() {
            return this.configFieldName;
        }

        String earlyMixinName() {
            return this.earlyMixinName;
        }

        String lateMixinName() {
            return this.lateMixinName;
        }

        boolean defaultValue() {
            return this.defaultValue;
        }

        List<CompatRule> compatibilityRules() {
            return this.compatibilityRules;
        }
    }
}
