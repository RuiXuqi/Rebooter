package fermiumbooter.rebooter.discovery;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Mod id paired with the display name and version that discovery found for it.
 * An unknown name or version is {@code null}.
 */
final class ModIdentity {
    private final String modId;
    @Nullable
    private final String name;
    @Nullable
    private final String version;

    private ModIdentity(String modId, @Nullable String name, @Nullable String version) {
        this.modId = modId;
        this.name = name;
        this.version = version;
    }

    static ModIdentity of(String modId, @Nullable String name, @Nullable String version) {
        return new ModIdentity(modId, trimToNull(name), trimToNull(version));
    }

    static ModIdentity presenceOnly(String modId) {
        return new ModIdentity(modId, null, null);
    }

    String modId() {
        return this.modId;
    }

    @Nullable
    String name() {
        return this.name;
    }

    @Nullable
    String version() {
        return this.version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModIdentity)) return false;
        ModIdentity identity = (ModIdentity) other;
        return this.modId.equals(identity.modId)
                && Objects.equals(this.name, identity.name)
                && Objects.equals(this.version, identity.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.modId, this.name, this.version);
    }

    @Override
    public String toString() {
        return this.modId + "(name=" + this.name + ";version=" + this.version + ')';
    }

    @Nullable
    private static String trimToNull(@Nullable String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
