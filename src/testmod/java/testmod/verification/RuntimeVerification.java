package testmod.verification;

import testmod.TestMod;
import testmod.fixture.registry.RegistryFixture;

import static testmod.verification.RuntimeProbe.Case;

public final class RuntimeVerification {

    private RuntimeVerification() {
    }

    public static void verify() {
        RuntimeProbe.requireApplied(Case.FORCED_CONFIG_EARLY);
        RuntimeProbe.requireApplied(Case.REGISTRY_EARLY);
        RuntimeProbe.requireApplied(Case.REGISTRY_LATE);
        RuntimeProbe.requireApplied(Case.CURRENT_LIFECYCLE_EARLY);
        RuntimeProbe.requireApplied(Case.CURRENT_LIFECYCLE_LATE);
        RuntimeProbe.requireApplied(Case.CURRENT_DEFAULT_LATE);
        RuntimeProbe.requireApplied(Case.CURRENT_COMPAT_WARNING_LATE);
        RuntimeProbe.requireNotApplied(Case.CURRENT_COMPAT_DISABLED_LATE);
        RuntimeProbe.requireApplied(Case.LEGACY_LIFECYCLE_EARLY);
        RuntimeProbe.requireApplied(Case.LEGACY_LIFECYCLE_LATE);
        RuntimeProbe.requireApplied(Case.LEGACY_NESTED_LATE);
        RuntimeProbe.requireApplied(Case.LEGACY_COMPAT_WARNING_LATE);
        RuntimeProbe.requireNotApplied(Case.LEGACY_COMPAT_DISABLED_EARLY);
        RegistryFixture.verify();
        if (!"mixin-squared".equals(new TestMod().transformedValue())) {
            throw new AssertionError("MixinSquared handler did not transform the test mixin");
        }
    }
}
