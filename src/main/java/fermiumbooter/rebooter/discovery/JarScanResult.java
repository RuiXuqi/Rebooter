package fermiumbooter.rebooter.discovery;

import java.util.*;

final class JarScanResult {
    private static final JarScanResult IGNORED = new JarScanResult(
            Status.IGNORED,
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.emptyList());

    private final Status status;
    private final Set<String> mappedModIds;
    private final Set<String> discoveredModIds;
    private final List<DiscoveredConfig> configResults;

    private JarScanResult(
            Status status,
            Iterable<String> mappedModIds,
            Iterable<String> discoveredModIds,
            List<DiscoveredConfig> configResults) {
        this.status = status;
        this.mappedModIds = immutableSet(mappedModIds);
        this.discoveredModIds = immutableSet(discoveredModIds);
        this.configResults = Collections.unmodifiableList(new ArrayList<>(configResults));
    }

    static JarScanResult scanned(
            Iterable<String> mappedModIds,
            String metadataModId,
            Iterable<String> annotationModIds,
            Iterable<String> launcherModIds,
            List<DiscoveredConfig> configResults) {
        return completed(
                Status.SCANNED,
                mappedModIds,
                metadataModId,
                annotationModIds,
                launcherModIds,
                configResults);
    }

    static JarScanResult cached(JarDiscoveryCache.CachedData cached) {
        return completed(
                Status.CACHED,
                cached.mappedModIds(),
                cached.metadataModId(),
                cached.annotationModIds(),
                cached.launcherModIds(),
                cached.configResults());
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

    Set<String> discoveredModIds() {
        return this.discoveredModIds;
    }

    List<DiscoveredConfig> configResults() {
        return this.configResults;
    }

    private static JarScanResult completed(
            Status status,
            Iterable<String> mappedModIds,
            String metadataModId,
            Iterable<String> annotationModIds,
            Iterable<String> launcherModIds,
            List<DiscoveredConfig> configResults) {
        Set<String> discoveredModIds = new HashSet<>();
        if (metadataModId != null) {
            discoveredModIds.add(metadataModId);
        }
        for (String modId : annotationModIds) {
            discoveredModIds.add(modId);
        }
        for (String modId : launcherModIds) {
            discoveredModIds.add(modId);
        }
        return new JarScanResult(status, mappedModIds, discoveredModIds, configResults);
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
