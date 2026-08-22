package fermiumbooter.rebooter.discovery;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import fermiumbooter.rebooter.RebooterConfig;
import fermiumbooter.rebooter.Reference;
import fermiumbooter.rebooter.util.ForgeConfigAccess;
import fermiumbooter.rebooter.util.GameDirectory;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class JarDiscovery {
    private static final ModIndex MOD_INDEX = new ModIndex();
    private static final List<DiscoveredConfig> CONFIG_RESULTS = new ArrayList<>();
    private static boolean indexed;
    private static boolean configsRegistered;
    private static int warningCount;
    private static DiscoveryStatistics statistics = new DiscoveryStatistics();

    private JarDiscovery() {
    }

    public static synchronized void registerConfigs() {
        indexOnce();
        if (configsRegistered) {
            return;
        }
        configsRegistered = true;
        long configStarted = statistics.start();
        int resultCount = CONFIG_RESULTS.size();
        for (DiscoveredConfig result : CONFIG_RESULTS) {
            for (DiscoveredConfig.Toggle toggle : result.toggles()) {
                boolean enabled = ForgeConfigAccess.findBoolean(
                        GameDirectory.resolve(),
                        result.configName(),
                        toggle.configFieldName(),
                        toggle.defaultValue());
                warningCount += ToggleRegistrar.register(
                        enabled,
                        toggle.earlyMixinName(),
                        toggle.lateMixinName(),
                        toggle.compatibilityRules());
            }
        }
        CONFIG_RESULTS.clear();
        statistics.configCompat(statistics.elapsed(configStarted), resultCount);
        statistics.logConfig();
    }

    public static synchronized boolean isModPresent(String modId) {
        if (StringUtils.isBlank(modId)) {
            return false;
        }
        indexOnce();
        return MOD_INDEX.isPresent(modId);
    }

    static synchronized ModIndex modIndex() {
        indexOnce();
        return MOD_INDEX;
    }

    public static synchronized void clear() {
        CONFIG_RESULTS.clear();
        ForgeConfigAccess.clearCompatibilityCache();
    }

    private static void indexOnce() {
        if (indexed) {
            return;
        }
        Stopwatch stopwatch = Stopwatch.createStarted();
        statistics = new DiscoveryStatistics();
        Map<String, Set<String>> packageMappings = RebooterConfig.modDiscoveryPackageMappings();
        Set<String> classScanAllowlist = RebooterConfig.discoveryClassScanAllowlist();

        long cacheLoadStarted = statistics.start();
        JarDiscoveryCache cache = JarDiscoveryCache.load(
                GameDirectory.resolve(),
                cacheProfile(classScanAllowlist, packageMappings));
        statistics.cacheLoad(statistics.elapsed(cacheLoadStarted));

        long candidateStarted = statistics.start();
        Set<File> candidates = JarCollector.collect(GameDirectory.resolve());
        statistics.candidateCollection(statistics.elapsed(candidateStarted), candidates.size());

        JarDiscoveryCache.ContentFingerprinter fingerprinter = new JarDiscoveryCache.ContentFingerprinter();
        FingerprintCollector.Batch preparedFingerprints = FingerprintCollector.collect(
                candidates,
                cache,
                fingerprinter);
        statistics.fingerprintBatch(
                preparedFingerprints.fingerprintCount(),
                preparedFingerprints.fingerprintBytes(),
                preparedFingerprints.elapsedNanos());

        JarCandidateScanner scanner = new JarCandidateScanner(
                packageMappings,
                classScanAllowlist,
                cache,
                preparedFingerprints,
                fingerprinter,
                statistics);
        List<ModIdentity> metadataIdentities = new ArrayList<>();
        Set<String> mappedModIds = new LinkedHashSet<>();
        Set<String> launcherModIds = new LinkedHashSet<>();
        List<ModIdentity> annotationIdentities = new ArrayList<>();
        List<DiscoveredConfig> configResults = new ArrayList<>();
        for (File candidate : candidates) {
            JarScanResult result = scanner.scan(candidate);
            if (result.isIgnored()) {
                continue;
            }
            if (result.metadataIdentity() != null) {
                metadataIdentities.add(result.metadataIdentity());
            }
            mappedModIds.addAll(result.mappedModIds());
            launcherModIds.addAll(result.launcherModIds());
            annotationIdentities.addAll(result.annotationIdentities());
            configResults.addAll(result.configResults());
        }

        long cacheSaveStarted = statistics.start();
        cache.save();
        statistics.cacheSave(statistics.elapsed(cacheSaveStarted));
        registerIdentities(metadataIdentities, mappedModIds, launcherModIds, annotationIdentities);
        CONFIG_RESULTS.addAll(configResults);
        indexed = true;
        Reference.LOGGER.info("ASM discovery completed in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        statistics.logIndex();
    }

    /**
     * Registers every discovered mod id, letting the later source win: built-ins, then
     * {@code mcmod.info}, then the sources that only prove presence, then {@code @Mod} annotations,
     * which restore the name and version a package mapping cleared.
     */
    private static void registerIdentities(
            List<ModIdentity> metadataIdentities,
            Set<String> mappedModIds,
            Set<String> launcherModIds,
            List<ModIdentity> annotationIdentities) {
        MOD_INDEX.overwrite(ModIdentity.of("minecraft", "Minecraft", "1.12.2"));
        MOD_INDEX.overwrite(ModIdentity.of("mcp", "Minecraft Coder Pack", "9.42"));
        MOD_INDEX.overwrite(ModIdentity.of("FML", "Forge Mod Loader", null));
        MOD_INDEX.overwrite(ModIdentity.of("forge", "Minecraft Forge", null));
        for (ModIdentity identity : metadataIdentities) {
            MOD_INDEX.overwrite(identity);
        }
        for (String modId : mappedModIds) {
            MOD_INDEX.overwrite(ModIdentity.presenceOnly(modId));
        }
        for (String modId : launcherModIds) {
            MOD_INDEX.addIfAbsent(modId);
        }
        for (ModIdentity identity : annotationIdentities) {
            MOD_INDEX.fillDeclared(identity);
        }
    }

    @VisibleForTesting
    static String cacheProfile(Set<String> scanAllowlist, Map<String, Set<String>> mappings) {
        StringBuilder profile = new StringBuilder(DiscoveryClassFilter.cacheProfile(scanAllowlist));
        profile.append(ClassMetadataScanner.cacheProfile());
        profile.append(JarCandidateScanner.cacheProfile());
        profile.append("--package-mappings--\n");
        List<String> packages = new ArrayList<>(mappings.keySet());
        Collections.sort(packages);
        for (String packagePrefix : packages) {
            profile.append(packagePrefix).append('=');
            List<String> modIds = new ArrayList<>(mappings.get(packagePrefix));
            Collections.sort(modIds);
            for (String modId : modIds) {
                profile.append(modId).append(',');
            }
            profile.append('\n');
        }
        return profile.toString();
    }

    public static int getWarningCount() {
        return warningCount;
    }

    @VisibleForTesting
    public static synchronized void resetForTesting() {
        indexed = false;
        configsRegistered = false;
        warningCount = 0;
        statistics = new DiscoveryStatistics();
        MOD_INDEX.clear();
        CONFIG_RESULTS.clear();
        ForgeConfigAccess.clearCompatibilityCache();
    }

    @VisibleForTesting
    static int getPrefilterScanCount() {
        return statistics.prefilterCount();
    }

    @VisibleForTesting
    static int getAsmClassReadCount() {
        return statistics.asmCount();
    }

    @VisibleForTesting
    static int getEnumeratedEntryCount() {
        return statistics.entryCount();
    }

    @VisibleForTesting
    static int getFingerprintReadCount() {
        return statistics.fingerprintCount();
    }
}
