package fermiumbooter.rebooter.discovery;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of the mod ids discovery knows about, each with the identity found for it.
 * Queries resolve a mod id to its registered key exactly when possible and ignoring case otherwise,
 * so presence and identity lookups always agree.
 */
final class ModIndex {
    private final Map<String, ModIdentity> identities = new LinkedHashMap<>();

    /**
     * Registers an identity, discarding whatever was known for the mod id before.
     */
    void overwrite(ModIdentity identity) {
        String key = this.key(identity.modId());
        this.identities.put(key, ModIdentity.of(key, identity.name(), identity.version()));
    }

    /**
     * Registers a mod id without an identity, leaving an already known identity untouched.
     */
    void addIfAbsent(String modId) {
        String key = this.key(modId);
        if (!this.identities.containsKey(key)) {
            this.identities.put(key, ModIdentity.presenceOnly(key));
        }
    }

    /**
     * Registers an identity, keeping the name and version already known for every field this
     * identity leaves unknown.
     */
    void fillDeclared(ModIdentity identity) {
        String key = this.key(identity.modId());
        ModIdentity known = this.identities.get(key);
        if (known == null) {
            this.identities.put(key, ModIdentity.of(key, identity.name(), identity.version()));
            return;
        }
        this.identities.put(key, ModIdentity.of(
                key,
                identity.name() == null ? known.name() : identity.name(),
                identity.version() == null ? known.version() : identity.version()));
    }

    boolean isPresent(String modId) {
        return StringUtils.isNotBlank(modId) && this.identities.containsKey(this.key(modId));
    }

    @Nullable
    ModIdentity identity(String modId) {
        return StringUtils.isBlank(modId) ? null : this.identities.get(this.key(modId));
    }

    Map<String, ModIdentity> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.identities));
    }

    void clear() {
        this.identities.clear();
    }

    private String key(String modId) {
        if (this.identities.containsKey(modId)) {
            return modId;
        }
        for (String registered : this.identities.keySet()) {
            if (modId.equalsIgnoreCase(registered)) {
                return registered;
            }
        }
        return modId;
    }
}
