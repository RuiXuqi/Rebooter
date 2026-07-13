package fermiumbooter.rebooter.discovery;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import fermiumbooter.rebooter.RebooterConfig;
import fermiumbooter.rebooter.Reference;
import fermiumbooter.rebooter.util.ForgeConfigAccess;
import fermiumbooter.rebooter.util.GameDirectory;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class JarDiscovery {
    private static final String OPTIFINE_TWEAKER = "optifine.OptiFineForgeTweaker";
    private static final Set<String> BUILTIN_MODS = new HashSet<>(Arrays.asList("minecraft", "mcp", "FML", "forge"));
    private static final Set<String> MAPPED_MODS = new HashSet<>();
    private static final Set<String> DISCOVERED_MODS = new HashSet<>();
    private static final List<ConfigReader.Result> CONFIG_RESULTS = new ArrayList<>();
    private static int prefilterScanCount;
    private static int asmClassReadCount;
    private static int enumeratedEntryCount;
    private static int fingerprintReadCount;
    private static boolean indexed;
    private static boolean configsRegistered;
    private static int warningCount;

    private JarDiscovery() {
    }

    public static synchronized void registerConfigs() {
        indexOnce();
        if (configsRegistered) return;
        configsRegistered = true;
        for (ConfigReader.Result result : CONFIG_RESULTS) {
            for (ConfigReader.Toggle toggle : result.toggles()) {
                boolean enabled = ForgeConfigAccess.findBoolean(GameDirectory.resolve(), result.configName(),
                        toggle.configFieldName(), toggle.defaultValue());
                warningCount += ToggleRegistrar.register(
                        enabled,
                        toggle.earlyMixinName(),
                        toggle.lateMixinName(),
                        toggle.compatibilityRules());
            }
        }
        CONFIG_RESULTS.clear();
    }

    public static synchronized boolean isModPresent(String modId) {
        if (StringUtils.isBlank(modId)) return false;
        indexOnce();
        return containsModId(BUILTIN_MODS, modId)
                || containsModId(DISCOVERED_MODS, modId)
                || containsModId(MAPPED_MODS, modId);
    }

    public static synchronized void clear() {
        CONFIG_RESULTS.clear();
        ForgeConfigAccess.clearCompatibilityCache();
    }

    @VisibleForTesting
    public static synchronized void resetForTesting() {
        indexed = false;
        configsRegistered = false;
        warningCount = 0;
        prefilterScanCount = 0;
        asmClassReadCount = 0;
        enumeratedEntryCount = 0;
        fingerprintReadCount = 0;
        MAPPED_MODS.clear();
        DISCOVERED_MODS.clear();
        CONFIG_RESULTS.clear();
        ForgeConfigAccess.clearCompatibilityCache();
    }

    private static void indexOnce() {
        if (indexed) return;
        Stopwatch stopwatch = Stopwatch.createStarted();
        Map<String, Set<String>> mappings = RebooterConfig.modDiscoveryPackageMappings();
        Set<String> scanAllowlist = RebooterConfig.discoveryClassScanAllowlist();
        Set<String> mappedMods = new HashSet<>();
        Set<String> discoveredMods = new HashSet<>();
        List<ConfigReader.Result> configResults = new ArrayList<>();
        JarDiscoveryCache cache = JarDiscoveryCache.load(
                GameDirectory.resolve(), cacheProfile(scanAllowlist, mappings));
        for (File candidate : JarCollector.collect(GameDirectory.resolve())) {
            scanJar(candidate, mappings, scanAllowlist, mappedMods, discoveredMods, configResults, cache);
        }
        cache.save();
        MAPPED_MODS.addAll(mappedMods);
        DISCOVERED_MODS.addAll(discoveredMods);
        CONFIG_RESULTS.addAll(configResults);
        indexed = true;
        Reference.LOGGER.info("ASM discovery completed in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @VisibleForTesting
    static String cacheProfile(Set<String> scanAllowlist, Map<String, Set<String>> mappings) {
        StringBuilder profile = new StringBuilder(DiscoveryClassFilter.cacheProfile(scanAllowlist));
        profile.append(ClassAnnotationScanner.cacheProfile());
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

    private static boolean containsModId(Set<String> modIds, String modId) {
        if (modIds.contains(modId)) return true;
        for (String candidate : modIds) {
            if (modId.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    private static void scanJar(
            File file,
            Map<String, Set<String>> mappings,
            Set<String> scanAllowlist,
            Set<String> mappedMods,
            Set<String> discoveredMods,
            List<ConfigReader.Result> configResults,
            JarDiscoveryCache cache) {
        JarDiscoveryCache.FileStamp initialStamp;
        byte[] fingerprint = null;
        JarDiscoveryCache.CachedData cached = null;
        try {
            initialStamp = JarDiscoveryCache.stamp(file);
            if (cache.hasLoadedEntry(file)) {
                fingerprint = contentFingerprint(file);
                cached = cache.lookup(file, initialStamp, fingerprint);
            }
        } catch (IOException e) {
            Reference.LOGGER.error("Failed to inspect discovery candidate {}", file, e);
            return;
        }
        if (cached != null) {
            List<ConfigReader.Result> cachedResults = new ArrayList<>();
            readCachedConfigs(file, cached.configClasses(), cachedResults);
            try {
                if (initialStamp.equals(JarDiscoveryCache.stamp(file))) {
                    restoreCachedIds(cached, mappedMods, discoveredMods);
                    configResults.addAll(cachedResults);
                    return;
                }
            } catch (IOException e) {
                Reference.LOGGER.error("Failed to verify cached discovery candidate {}", file, e);
            }
            Reference.LOGGER.warn("Cached discovery candidate changed while being read; ignoring {}", file);
            return;
        }
        try (JarFile jar = new JarFile(file, false)) {
            Set<String> jarLauncherModIds = new HashSet<>();
            try {
                Manifest manifest = jar.getManifest();
                if (manifest != null
                        && OPTIFINE_TWEAKER.equals(manifest.getMainAttributes().getValue("TweakClass"))) {
                    jarLauncherModIds.add("optifine");
                }
            } catch (IOException e) {
                Reference.LOGGER.debug("Skipping unreadable manifest in {}", file, e);
            }
            List<JarEntry> classes = new ArrayList<>();
            JarEntry mcmodInfo = null;
            Set<String> jarFallbackMods = new HashSet<>();
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                enumeratedEntryCount++;
                String entryName = entry.getName();
                if ("mcmod.info".equals(entryName)) mcmodInfo = entry;
                for (Map.Entry<String, Set<String>> mapping : mappings.entrySet()) {
                    if (entryName.startsWith(mapping.getKey())) jarFallbackMods.addAll(mapping.getValue());
                }
                if (DiscoveryClassFilter.isScannable(entry, scanAllowlist)) classes.add(entry);
            }
            List<ConfigReader.Result> scannedResults = new ArrayList<>();
            List<String> configClasses = new ArrayList<>();
            String jarMetadataModId = null;
            Set<String> jarAnnotationModIds = new HashSet<>();
            if (mcmodInfo != null) {
                try (InputStream input = jar.getInputStream(mcmodInfo)) {
                    jarMetadataModId = JsonInfoReader.firstModId(input);
                } catch (IOException e) {
                    Reference.LOGGER.debug("Skipping unreadable mcmod.info in {}", file, e);
                }
            }
            ClassAnnotationScanner scanner = new ClassAnnotationScanner();
            for (JarEntry entry : classes) {
                prefilterScanCount++;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassAnnotationScanner.ScanResult scan = scanner.scan(input, entry.getSize());
                    if (!scan.isEmpty()) asmClassReadCount++;
                    if (scan.has(ClassAnnotationScanner.MIXIN_CONFIG)) {
                        ConfigReader.Result result = ConfigReader.scan(scan.classBytes());
                        if (result != null) {
                            scannedResults.add(result);
                            configClasses.add(entry.getName());
                        }
                    }
                    if (scan.has(ClassAnnotationScanner.FORGE_MOD)) {
                        String modId = ModAnnotationReader.scan(scan.classBytes());
                        if (modId != null) jarAnnotationModIds.add(modId);
                    }
                } catch (IOException | RuntimeException e) {
                    Reference.LOGGER.debug("Skipping unreadable class '{}' in {}",
                            entry.getName(), file, e);
                }
            }
            JarDiscoveryCache.FileStamp finalStamp = JarDiscoveryCache.stamp(file);
            if (!initialStamp.equals(finalStamp)) {
                Reference.LOGGER.warn("Discovery candidate changed while reading classes; ignoring {}", file);
                return;
            }
            if (cache.isEnabled() && fingerprint == null) {
                fingerprint = contentFingerprint(file);
                if (!finalStamp.equals(JarDiscoveryCache.stamp(file))) {
                    Reference.LOGGER.warn("Discovery candidate changed while fingerprinting; ignoring {}", file);
                    return;
                }
            }
            mappedMods.addAll(jarFallbackMods);
            if (jarMetadataModId != null) discoveredMods.add(jarMetadataModId);
            discoveredMods.addAll(jarAnnotationModIds);
            discoveredMods.addAll(jarLauncherModIds);
            configResults.addAll(scannedResults);
            if (cache.isEnabled()) {
                cache.record(
                        file,
                        finalStamp,
                        fingerprint,
                        configClasses,
                        jarMetadataModId,
                        jarAnnotationModIds,
                        jarFallbackMods,
                        jarLauncherModIds);
            }
        } catch (IOException e) {
            Reference.LOGGER.error("Failed to scan discovery metadata in {}", file, e);
        }
    }

    private static void restoreCachedIds(
            JarDiscoveryCache.CachedData cached,
            Set<String> mappedMods,
            Set<String> discoveredMods) {
        mappedMods.addAll(cached.mappedModIds());
        if (cached.metadataModId() != null) discoveredMods.add(cached.metadataModId());
        discoveredMods.addAll(cached.annotationModIds());
        discoveredMods.addAll(cached.launcherModIds());
    }

    private static byte[] contentFingerprint(File file) throws IOException {
        fingerprintReadCount++;
        return JarDiscoveryCache.contentFingerprint(file);
    }

    private static void readCachedConfigs(
            File file,
            List<String> cachedConfigClasses,
            List<ConfigReader.Result> configResults) {
        if (cachedConfigClasses.isEmpty()) return;
        try (JarFile jar = new JarFile(file, false)) {
            readCachedConfigs(jar, file, cachedConfigClasses, configResults);
        } catch (IOException e) {
            Reference.LOGGER.error("Failed to read cached discovery metadata in {}", file, e);
        }
    }

    private static void readCachedConfigs(
            JarFile jar,
            File file,
            List<String> cachedConfigClasses,
            List<ConfigReader.Result> configResults) {
        for (String className : cachedConfigClasses) {
            JarEntry entry = jar.getJarEntry(className);
            if (entry == null) return;
            try (InputStream input = jar.getInputStream(entry)) {
                ConfigReader.Result result = ConfigReader.scan(input);
                if (result != null) configResults.add(result);
            } catch (IOException | RuntimeException e) {
                Reference.LOGGER.debug("Skipping unreadable cached config class '{}' in {}",
                        className, file, e);
            }
        }
    }

    // TODO
    public static int getWarningCount() {
        return warningCount;
    }

    @VisibleForTesting
    static int getPrefilterScanCount() {
        return prefilterScanCount;
    }

    @VisibleForTesting
    static int getAsmClassReadCount() {
        return asmClassReadCount;
    }

    @VisibleForTesting
    static int getEnumeratedEntryCount() {
        return enumeratedEntryCount;
    }

    @VisibleForTesting
    static int getFingerprintReadCount() {
        return fingerprintReadCount;
    }
}
