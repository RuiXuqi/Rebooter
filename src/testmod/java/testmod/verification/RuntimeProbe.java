package testmod.verification;

import java.util.EnumSet;
import java.util.Set;

public final class RuntimeProbe {

    private static final Set<Case> APPLIED = EnumSet.noneOf(Case.class);

    private RuntimeProbe() {
    }

    public static void mark(Case testCase) {
        APPLIED.add(testCase);
    }

    static void requireApplied(Case testCase) {
        if (!APPLIED.contains(testCase)) {
            throw new AssertionError(testCase + " mixin was not applied");
        }
    }

    static void requireNotApplied(Case testCase) {
        if (APPLIED.contains(testCase)) {
            throw new AssertionError(testCase + " mixin should have been disabled");
        }
    }

    public enum Case {
        FORCED_CONFIG_EARLY,
        REGISTRY_EARLY,
        REGISTRY_LATE,
        CURRENT_LIFECYCLE_EARLY,
        CURRENT_LIFECYCLE_LATE,
        CURRENT_DEFAULT_LATE,
        CURRENT_COMPAT_WARNING_LATE,
        CURRENT_COMPAT_DISABLED_LATE,
        LEGACY_LIFECYCLE_EARLY,
        LEGACY_LIFECYCLE_LATE,
        LEGACY_NESTED_LATE,
        LEGACY_COMPAT_WARNING_LATE,
        LEGACY_COMPAT_DISABLED_EARLY
    }
}
