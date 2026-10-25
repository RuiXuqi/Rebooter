package fermiumbooter.rebooter.discovery;

import javax.annotation.Nullable;
import java.util.*;

final class JarScanResult {
    private static final JarScanResult IGNORED = new JarScanResult(
            Status.IGNORED,
            Collections.emptySet(),
            null,
            Collections.emptyList(),
            Collections.emptySet(),
            Collections.emptyList(),
            false);

    private final Status status;
    private final Set<String> mappedModIds;
    @Nullable
    private final ModIdentity metadataIdentity;
    private final List<ModIdentity> annotationIdentities;
    private final Set<String> launcherModIds;
    private final List<DiscoveredConfig> configResults;
    private final boolean exposesConfigClasses;

    private JarScanResult(
            Status status,
            Iterable<String> mappedModIds,
            @Nullable ModIdentity metadataIdentity,
            Collection<ModIdentity> annotationIdentities,
            Iterable<String> launcherModIds,
            List<DiscoveredConfig> configResults,
            boolean exposesConfigClasses) {
        this.status = status;
        this.mappedModIds = immutableSet(mappedModIds);
        this.metadataIdentity = metadataIdentity;
        this.annotationIdentities = Collections.unmodifiableList(new ArrayList<>(annotationIdentities));
        this.launcherModIds = immutableSet(launcherModIds);
        this.configResults = Collections.unmodifiableList(new ArrayList<>(configResults));
        this.exposesConfigClasses = exposesConfigClasses;
    }

    static JarScanResult scanned(
            Iterable<String> mappedModIds,
            @Nullable ModIdentity metadataIdentity,
            Collection<ModIdentity> annotationIdentities,
            Iterable<String> launcherModIds,
            List<DiscoveredConfig> configResults) {
        return new JarScanResult(
                Status.SCANNED,
                mappedModIds,
                metadataIdentity,
                annotationIdentities,
                launcherModIds,
                configResults,
                !configResults.isEmpty());
    }

    static JarScanResult cached(JarDiscoveryCache.CachedData cached) {
        return new JarScanResult(
                Status.CACHED,
                cached.mappedModIds(),
                cached.metadataIdentity(),
                cached.annotationIdentities(),
                cached.launcherModIds(),
                cached.configResults(),
                !cached.configResults().isEmpty());
    }

    static JarScanResult ignored() {
        return IGNORED;
    }

    boolean isIgnored() {
        return this.status == Status.IGNORED;
    }

    Set<String> mappedModIds() {
        return this.mappedModIds;
    }

    @Nullable
    ModIdentity metadataIdentity() {
        return this.metadataIdentity;
    }

    List<ModIdentity> annotationIdentities() {
        return this.annotationIdentities;
    }

    Set<String> launcherModIds() {
        return this.launcherModIds;
    }

    List<DiscoveredConfig> configResults() {
        return this.configResults;
    }

    boolean exposesConfigClasses() {
        return this.exposesConfigClasses;
    }

    private static Set<String> immutableSet(Iterable<String> values) {
        Set<String> copy = new HashSet<>();
        for (String value : values) {
            copy.add(value);
        }
        return Collections.unmodifiableSet(copy);
    }

    private enum Status {
        SCANNED,
        CACHED,
        IGNORED
    }
}
