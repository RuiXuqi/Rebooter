package fermiumbooter.rebooter.discovery;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import fermiumbooter.rebooter.RebooterConfig;
import fermiumbooter.rebooter.Reference;
import fermiumbooter.rebooter.util.ForgeConfigAccess;
import fermiumbooter.rebooter.util.GameDirectory;
import net.minecraftforge.fml.relauncher.libraries.Artifact;
import net.minecraftforge.fml.relauncher.libraries.LibraryManager;
import net.minecraftforge.fml.relauncher.libraries.Repository;
import org.apache.commons.lang3.StringUtils;
import zone.rong.mixinbooter.service.ModDiscoverer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public final class JarDiscovery {
    private static final Set<String> BUILTIN_MODS = new HashSet<>(Arrays.asList("minecraft", "mcp", "FML", "forge"));
    private static final Set<String> FALLBACK_MODS = new HashSet<>();
    private static final List<ConfigReader.Result> CONFIG_RESULTS = new ArrayList<>();
    private static int prefilterScanCount;
    private static int enumeratedEntryCount;
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
        if (knownByLoader(modId)) return true;
        if (containsModId(RebooterConfig.modDiscoveryTargets(), modId)) {
            indexOnce();
            return containsModId(FALLBACK_MODS, modId);
        }
        return false;
    }

    public static synchronized void clear() {
        indexed = false;
        configsRegistered = false;
        warningCount = 0;
        prefilterScanCount = 0;
        enumeratedEntryCount = 0;
        FALLBACK_MODS.clear();
        CONFIG_RESULTS.clear();
        ForgeConfigAccess.clearCompatibilityCache();
    }

    private static void indexOnce() {
        if (indexed) return;
        Stopwatch stopwatch = Stopwatch.createStarted();
        Map<String, Set<String>> mappings = RebooterConfig.modDiscoveryPackageMappings();
        Set<String> scanAllowlist = RebooterConfig.mixinConfigScanAllowlist();
        Set<String> fallbackMods = new HashSet<>();
        List<ConfigReader.Result> configResults = new ArrayList<>();
        JarDiscoveryCache cache = JarDiscoveryCache.load(
                GameDirectory.resolve(), cacheProfile(scanAllowlist, mappings));
        for (File candidate : candidates()) {
            scanJar(candidate, mappings, scanAllowlist, fallbackMods, configResults, cache);
        }
        cache.save();
        FALLBACK_MODS.addAll(fallbackMods);
        CONFIG_RESULTS.addAll(configResults);
        indexed = true;
        Reference.LOGGER.info("ASM discovery completed in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    private static boolean knownByLoader(String modId) {
        return containsModId(BUILTIN_MODS, modId) || containsModId(ModDiscoverer.getPresentMods(), modId);
    }

    @VisibleForTesting
    static String cacheProfile(Set<String> scanAllowlist, Map<String, Set<String>> mappings) {
        StringBuilder profile = new StringBuilder(MixinConfigClassFilter.cacheProfile(scanAllowlist));
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

    @VisibleForTesting
    static Set<File> candidates() {
        Set<File> candidates = new LinkedHashSet<>();

        File gameDirectory = GameDirectory.resolve();
        for (File candidate : LibraryManager.gatherLegacyCanidates(gameDirectory)) {
            addCandidate(candidates, candidate);
        }
        for (Artifact artifact : LibraryManager.flattenLists(gameDirectory)) {
            Artifact resolved = Repository.resolveAll(artifact);
            if (resolved != null) addCandidate(candidates, resolved.getFile());
        }

        for (String modId : ModDiscoverer.getPresentMods()) {
            for (File source : ModDiscoverer.getModSources(modId)) {
                addCandidate(candidates, source);
            }
        }

        // For dev
        String classPath = System.getProperty("java.class.path", "");
        for (String entry : classPath.split(Pattern.quote(File.pathSeparator))) {
            File file = new File(entry);
            if (file.isFile() && entry.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                addCandidate(candidates, file);
            }
        }
        return candidates;
    }

    private static void addCandidate(Set<File> candidates, File candidate) {
        if (candidate == null) return;
        try {
            candidates.add(candidate.getCanonicalFile());
        } catch (IOException ignored) {
            candidates.add(candidate.getAbsoluteFile().toPath().normalize().toFile());
        }
    }

    private static void scanJar(
            File file,
            Map<String, Set<String>> mappings,
            Set<String> scanAllowlist,
            Set<String> fallbackMods,
            List<ConfigReader.Result> configResults,
            JarDiscoveryCache cache) {
        JarDiscoveryCache.FileStamp initialStamp;
        try {
            initialStamp = JarDiscoveryCache.stamp(file);
        } catch (IOException e) {
            Reference.LOGGER.error("Failed to inspect discovery candidate {}", file, e);
            return;
        }
        JarDiscoveryCache.CachedData cached = cache.lookup(file, initialStamp);
        if (cached != null) {
            List<ConfigReader.Result> cachedResults = new ArrayList<>();
            readCachedConfigs(file, cached.configClasses(), cachedResults);
            try {
                if (initialStamp.equals(JarDiscoveryCache.stamp(file))) {
                    fallbackMods.addAll(cached.fallbackMods());
                    configResults.addAll(cachedResults);
                    return;
                }
                initialStamp = JarDiscoveryCache.stamp(file);
            } catch (IOException e) {
                Reference.LOGGER.error("Failed to verify cached discovery candidate {}", file, e);
                return;
            }
        }
        try (JarFile jar = new JarFile(file, false)) {
            JarDiscoveryCache.Fingerprint fingerprint = JarDiscoveryCache.fingerprint();
            List<JarEntry> classes = new ArrayList<>();
            Set<String> jarFallbackMods = new HashSet<>();
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                enumeratedEntryCount++;
                fingerprint.add(entry);
                String entryName = entry.getName();
                for (Map.Entry<String, Set<String>> mapping : mappings.entrySet()) {
                    if (entryName.startsWith(mapping.getKey())) jarFallbackMods.addAll(mapping.getValue());
                }
                if (MixinConfigClassFilter.isScannable(entry, scanAllowlist)) classes.add(entry);
            }
            byte[] jarFingerprint = fingerprint.finish();
            JarDiscoveryCache.FileStamp scannedStamp = JarDiscoveryCache.stamp(file);
            boolean stable = initialStamp.equals(scannedStamp);
            if (!stable) {
                Reference.LOGGER.warn("Discovery candidate changed while being enumerated; ignoring {}", file);
                return;
            }
            cached = cache.lookup(file, scannedStamp, jarFingerprint);
            if (cached != null) {
                List<ConfigReader.Result> cachedResults = new ArrayList<>();
                readCachedConfigs(jar, file, cached.configClasses(), cachedResults);
                if (!scannedStamp.equals(JarDiscoveryCache.stamp(file))) {
                    Reference.LOGGER.warn("Discovery candidate changed while reading cached metadata; ignoring {}", file);
                    return;
                }
                fallbackMods.addAll(cached.fallbackMods());
                configResults.addAll(cachedResults);
                return;
            }
            List<ConfigReader.Result> scannedResults = new ArrayList<>();
            List<String> configClasses = new ArrayList<>();
            MixinConfigScanner scanner = new MixinConfigScanner();
            for (JarEntry entry : classes) {
                prefilterScanCount++;
                try (InputStream input = jar.getInputStream(entry)) {
                    ConfigReader.Result result = scanner.scanIfPresent(input);
                    if (result != null) {
                        scannedResults.add(result);
                        configClasses.add(entry.getName());
                    }
                } catch (IOException | IllegalArgumentException e) {
                    Reference.LOGGER.debug("Skipping unreadable class '{}' in {}",
                            entry.getName(), file, e);
                }
            }
            if (!scannedStamp.equals(JarDiscoveryCache.stamp(file))) {
                Reference.LOGGER.warn("Discovery candidate changed while reading classes; ignoring {}", file);
                return;
            }
            fallbackMods.addAll(jarFallbackMods);
            configResults.addAll(scannedResults);
            cache.record(file, scannedStamp, jarFingerprint, configClasses, jarFallbackMods);
        } catch (IOException e) {
            Reference.LOGGER.error("Failed to scan discovery metadata in {}", file, e);
        }
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
            } catch (IOException | IllegalArgumentException e) {
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
    static int getEnumeratedEntryCount() {
        return enumeratedEntryCount;
    }
}
