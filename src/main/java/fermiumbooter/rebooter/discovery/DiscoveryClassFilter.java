package fermiumbooter.rebooter.discovery;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;

final class DiscoveryClassFilter {
    private static final String[] SKIPPED_CLASS_PREFIXES = new String[]{
            "META-INF/",
            "__MACOSX/",

            "org/spongepowered/",
            "com/llamalad7/mixinextras/",
            "com/bawnorton/mixinsquared/",
            "org/objectweb/asm/",

            "it/unimi/",
            "com/google/common/",
            "com/google/gson/",
            "org/apache/commons/",

            "org/apache/logging/",
            "org/slf4j/",
            "io/netty/",
            "gnu/trove/",
            "org/joml/",

            "kotlin/",
            "kotlinx/",
            "scala/",
            "groovy/",
            "org/codehaus/groovy/",
            "clojure/"
    };

    private DiscoveryClassFilter() {
    }

    static boolean isScannable(JarEntry entry, Set<String> allowedPrefixes) {
        if (entry.isDirectory()) return false;
        String name = entry.getName();
        if (!name.endsWith(".class")
                || name.endsWith("/module-info.class") || name.equals("module-info.class")
                || name.endsWith("/package-info.class") || name.equals("package-info.class"))
            return false;
        if (startsWithAny(name, allowedPrefixes)) return true;
        return !startsWithAny(name, SKIPPED_CLASS_PREFIXES);
    }

    static String cacheProfile(Set<String> allowedPrefixes) {
        List<String> sortedAllowedPrefixes = new ArrayList<>(allowedPrefixes);
        Collections.sort(sortedAllowedPrefixes);
        StringBuilder profile = new StringBuilder("discovery-class-filter\n");
        append(profile, SKIPPED_CLASS_PREFIXES);
        profile.append("--allowlist--\n");
        append(profile, sortedAllowedPrefixes);
        return profile.toString();
    }

    private static boolean startsWithAny(String name, Iterable<String> prefixes) {
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    private static boolean startsWithAny(String name, String[] prefixes) {
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    private static void append(StringBuilder destination, Iterable<String> values) {
        for (String value : values) {
            destination.append(value).append('\n');
        }
    }

    private static void append(StringBuilder destination, String[] values) {
        for (String value : values) {
            destination.append(value).append('\n');
        }
    }

    @VisibleForTesting
    static String[] skippedClassPrefixes() {
        return SKIPPED_CLASS_PREFIXES;
    }
}
