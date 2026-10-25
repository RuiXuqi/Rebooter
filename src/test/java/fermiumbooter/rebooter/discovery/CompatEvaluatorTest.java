package fermiumbooter.rebooter.discovery;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CompatEvaluatorTest {

    @Test
    void desiredModThatIsAbsentIsAnIssue() {
        CompatEvaluator.Outcome outcome = evaluate(index(), rule("jei", null, null, true));

        assertTrue(outcome.disabled());
        assertEquals(1, outcome.warnings());
    }

    @Test
    void desiredModThatIsPresentIsNoIssue() {
        CompatEvaluator.Outcome outcome = evaluate(
                index(ModIdentity.of("jei", "Just Enough Items", "4.16.1")),
                rule("jei", null, null, true));

        assertFalse(outcome.disabled());
        assertEquals(0, outcome.warnings());
    }

    @Test
    void desiredModWhoseNameDiffersIsAnIssue() {
        CompatEvaluator.Outcome outcome = evaluate(
                index(ModIdentity.of("jei", "Just Enough Items Fork", "4.16.1")),
                rule("jei", "Just Enough Items", null, true));

        assertTrue(outcome.disabled());
        assertEquals(1, outcome.warnings());
    }

    @Test
    void undesiredModThatIsPresentIsAnIssue() {
        CompatEvaluator.Outcome outcome = evaluate(
                index(ModIdentity.of("jei", "Just Enough Items", "4.16.1")),
                rule("jei", "Just Enough Items", null, false));

        assertTrue(outcome.disabled());
        assertEquals(1, outcome.warnings());
    }

    @Test
    void undesiredModWhoseNameDiffersIsNoIssue() {
        CompatEvaluator.Outcome outcome = evaluate(
                index(ModIdentity.of("jei", "Just Enough Items Fork", "4.16.1")),
                rule("jei", "Just Enough Items", null, false));

        assertFalse(outcome.disabled());
        assertEquals(0, outcome.warnings());
    }

    @Test
    void undesiredModThatIsAbsentIsNoIssue() {
        CompatEvaluator.Outcome outcome = evaluate(index(), rule("jei", "Just Enough Items", null, false));

        assertFalse(outcome.disabled());
        assertEquals(0, outcome.warnings());
    }

    @Test
    void unknownDisplayNameNeverMatchesARequiredName() {
        CompatEvaluator.Outcome outcome = evaluate(
                index(ModIdentity.presenceOnly("optifine")),
                rule("optifine", "OptiFine", null, true));

        assertTrue(outcome.disabled());
    }

    @Test
    void requiredNameIsCaseSensitive() {
        CompatEvaluator.Outcome outcome = evaluate(
                index(ModIdentity.of("jei", "just enough items", null)),
                rule("jei", "Just Enough Items", null, true));

        assertTrue(outcome.disabled());
    }

    @Test
    void unknownVersionSatisfiesNoRange() {
        CompatEvaluator.Outcome outcome = evaluate(
                index(ModIdentity.of("jei", "Just Enough Items", null)),
                rule("jei", null, "[4.0,5.0)", true));

        assertTrue(outcome.disabled());
    }

    @Test
    void versionInsideTheRangeFits() {
        CompatEvaluator.Outcome outcome = evaluate(
                index(ModIdentity.of("jei", "Just Enough Items", "4.16.1")),
                rule("jei", null, "[4.0,5.0)", true));

        assertFalse(outcome.disabled());
    }

    @Test
    void versionOutsideTheRangeDoesNotFit() {
        CompatEvaluator.Outcome outcome = evaluate(
                index(ModIdentity.of("jei", "Just Enough Items", "5.0.1")),
                rule("jei", null, "[4.0,5.0)", true));

        assertTrue(outcome.disabled());
    }

    @Test
    void rangeEqualToTheVersionStringFitsExactly() throws Exception {
        // Forge parses "[1.5]" as a real range, which the literal version string does not fall into.
        assertFalse(VersionRange.createFromVersionSpec("[1.5]")
                .containsVersion(new DefaultArtifactVersion("[1.5]")));

        CompatEvaluator.Outcome outcome = evaluate(
                index(ModIdentity.of("weird", "Weird Mod", "[1.5]")),
                rule("weird", null, "[1.5]", true));

        assertFalse(outcome.disabled());
    }

    @Test
    void invalidRangeLeavesTheVersionUnchecked() {
        CompatEvaluator.Outcome outcome = evaluate(
                index(ModIdentity.of("jei", "Just Enough Items", "4.16.1")),
                rule("jei", null, "[4.0,", true));

        assertFalse(outcome.disabled());
        assertEquals(0, outcome.warnings());
    }

    @Test
    void aVersionInRangeCannotRescueAWrongName() {
        CompatEvaluator.Outcome outcome = evaluate(
                index(ModIdentity.of("jei", "Just Enough Items Fork", "4.16.1")),
                rule("jei", "Just Enough Items", "[4.0,5.0)", true));

        assertTrue(outcome.disabled());
    }

    @Test
    void warnIngameDecidesWhetherAFailureIsCounted() {
        CompatEvaluator.Outcome counted = evaluate(
                index(),
                new CompatRule("jei", null, null, true, true, true, "counted"));
        CompatEvaluator.Outcome silent = evaluate(
                index(),
                new CompatRule("jei", null, null, true, true, false, "silent"));

        assertEquals(1, counted.warnings());
        assertEquals(0, silent.warnings());
        assertTrue(counted.disabled());
        assertTrue(silent.disabled());
    }

    @Test
    void aFailureThatOnlyWarnsKeepsTheMixinQueued() {
        CompatEvaluator.Outcome outcome = evaluate(
                index(),
                new CompatRule("jei", null, null, true, false, true, "warning only"));

        assertFalse(outcome.disabled());
        assertEquals(1, outcome.warnings());
    }

    @Test
    void everyFailingRuleIsCountedAndOneDisablingRuleIsEnough() {
        CompatEvaluator.Outcome outcome = evaluate(
                index(ModIdentity.of("forge", "Minecraft Forge", null)),
                new CompatRule("jei", null, null, true, false, true, "missing"),
                new CompatRule("forge", null, null, false, true, true, "present"));

        assertTrue(outcome.disabled());
        assertEquals(2, outcome.warnings());
    }

    private static ModIndex index(ModIdentity... identities) {
        ModIndex index = new ModIndex();
        for (ModIdentity identity : identities) {
            index.overwrite(identity);
        }
        return index;
    }

    private static CompatEvaluator.Outcome evaluate(ModIndex index, CompatRule... rules) {
        return CompatEvaluator.evaluate(index, Arrays.asList(rules), "late 'mixins.evaluator.json'");
    }

    private static CompatRule rule(String modId, String modName, String range, boolean desired) {
        return new CompatRule(modId, modName, range, desired, true, true, "test reason");
    }
}
