package fermiumbooter.rebooter.discovery;

import fermiumbooter.rebooter.Reference;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import net.minecraftforge.fml.common.versioning.DefaultArtifactVersion;
import net.minecraftforge.fml.common.versioning.InvalidVersionSpecificationException;
import net.minecraftforge.fml.common.versioning.VersionRange;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Judges the {@code @CompatHandling} conditions declared for a single Mixin toggle.
 */
final class CompatEvaluator {

    private CompatEvaluator() {
    }

    static Outcome evaluate(ModIndex index, List<CompatRule> rules, String configDescription) {
        boolean disabled = false;
        int warnings = 0;
        for (CompatRule rule : rules) {
            boolean present = index.isPresent(rule.modid());
            boolean issue = rule.desired() != present;
            // A present mod failing the name or version constraint is a different mod than the rule
            // describes, which inverts the outcome: a desired one is missing, an undesired one absent.
            if (present && !fits(index.identity(rule.modid()), rule, configDescription)) {
                issue = !issue;
            }
            if (!issue) {
                continue;
            }
            if (rule.warnIngame()) {
                warnings++;
            }
            if (rule.disableMixin()) {
                disabled = true;
                Reference.LOGGER.error(
                        "Mixin config {} was disabled: {}", configDescription, describe(rule, present));
            } else {
                Reference.LOGGER.warn(
                        "Mixin config {} may have issues: {}", configDescription, describe(rule, present));
            }
        }
        return new Outcome(disabled, warnings);
    }

    private static boolean fits(
            @Nullable ModIdentity identity,
            CompatRule rule,
            String configDescription) {
        String name = identity == null ? null : identity.name();
        if (rule.modName() != null && !rule.modName().equals(name)) {
            return false;
        }
        if (rule.modVersionRange() == null) {
            return true;
        }
        return inVersionRange(
                identity == null ? null : identity.version(),
                rule.modVersionRange(),
                rule.modid(),
                configDescription);
    }

    private static boolean inVersionRange(
            @Nullable String version,
            String range,
            String modId,
            String configDescription) {
        ArtifactVersion parsed = parse(version);
        if (parsed == null) {
            return false;
        }
        // An exact version is a range of one, which VersionRange does not treat as a restriction.
        if (range.equals(parsed.getVersionString())) {
            return true;
        }
        try {
            return VersionRange.createFromVersionSpec(range).containsVersion(parsed);
        } catch (InvalidVersionSpecificationException | RuntimeException e) {
            Reference.LOGGER.warn(
                    "Ignoring invalid target version range '{}' of mod '{}' in Mixin config {}",
                    range,
                    modId,
                    configDescription);
            return true;
        }
    }

    @Nullable
    private static ArtifactVersion parse(@Nullable String version) {
        if (version == null) {
            return null;
        }
        try {
            return new DefaultArtifactVersion(version);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String describe(CompatRule rule, boolean present) {
        StringBuilder description = new StringBuilder(rule.desired() ? "desired" : "undesired")
                .append(" mod '")
                .append(rule.modid())
                .append("' is ")
                .append(present ? "present" : "absent");
        if (rule.modName() != null) {
            description.append(", required name '").append(rule.modName()).append('\'');
        }
        if (rule.modVersionRange() != null) {
            description.append(", required version range '").append(rule.modVersionRange()).append('\'');
        }
        if (StringUtils.isNotBlank(rule.reason())) {
            description.append(" (reason=").append(rule.reason()).append(')');
        }
        return description.toString();
    }

    static final class Outcome {
        private final boolean disabled;
        private final int warnings;

        private Outcome(boolean disabled, int warnings) {
            this.disabled = disabled;
            this.warnings = warnings;
        }

        boolean disabled() {
            return this.disabled;
        }

        int warnings() {
            return this.warnings;
        }
    }
}
