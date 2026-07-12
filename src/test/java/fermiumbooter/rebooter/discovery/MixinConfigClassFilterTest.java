package fermiumbooter.rebooter.discovery;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.jar.JarEntry;

import static org.junit.jupiter.api.Assertions.*;

class MixinConfigClassFilterTest {

    @Test
    void skipsEveryDefaultLibraryPrefix() {
        for (String prefix : MixinConfigClassFilter.skippedClassPrefixes()) {
            assertFalse(
                    MixinConfigClassFilter.isScannable(
                            new JarEntry(prefix + "LibraryClass.class"), Collections.emptySet()),
                    prefix);
        }
    }

    @Test
    void allowlistOverridesDefaultPrefixForASubpackage() {
        Set<String> allowlist = new HashSet<>(Collections.singletonList("org/spongepowered/example/"));

        assertTrue(MixinConfigClassFilter.isScannable(
                new JarEntry("org/spongepowered/example/Config.class"), allowlist));
        assertFalse(MixinConfigClassFilter.isScannable(
                new JarEntry("org/spongepowered/asm/Mixin.class"), allowlist));

        assertTrue(MixinConfigClassFilter.isScannable(
                new JarEntry("META-INF/example/Config.class"),
                new HashSet<>(Collections.singletonList("META-INF/example/"))));
    }

    @Test
    void keepsInnerClassesButRejectsStructuralClassFiles() {
        assertTrue(MixinConfigClassFilter.isScannable(
                new JarEntry("example/Config$Nested.class"), Collections.emptySet()));
        assertFalse(MixinConfigClassFilter.isScannable(
                new JarEntry("module-info.class"), Collections.emptySet()));
        assertFalse(MixinConfigClassFilter.isScannable(
                new JarEntry("example/package-info.class"), Collections.emptySet()));
    }

    @Test
    void cacheProfileIsOrderIndependentButChangesWithTheAllowlist() {
        Set<String> first = new HashSet<>();
        first.add("org/spongepowered/example/");
        first.add("com/google/common/special/");
        Set<String> reordered = new HashSet<>();
        reordered.add("com/google/common/special/");
        reordered.add("org/spongepowered/example/");

        assertEquals(
                MixinConfigClassFilter.cacheProfile(first),
                MixinConfigClassFilter.cacheProfile(reordered));
        assertNotEquals(
                MixinConfigClassFilter.cacheProfile(first),
                MixinConfigClassFilter.cacheProfile(Collections.emptySet()));
    }

    @Test
    void discoveryCacheProfileIncludesOrderIndependentPackageMappings() {
        Map<String, Set<String>> first = new LinkedHashMap<>();
        first.put("example/", new LinkedHashSet<>(java.util.Arrays.asList("second", "first")));
        Map<String, Set<String>> reordered = new LinkedHashMap<>();
        reordered.put("example/", new LinkedHashSet<>(java.util.Arrays.asList("first", "second")));
        Map<String, Set<String>> changed = new LinkedHashMap<>();
        changed.put("example/", Collections.singleton("first"));

        assertEquals(
                JarDiscovery.cacheProfile(Collections.emptySet(), first),
                JarDiscovery.cacheProfile(Collections.emptySet(), reordered));
        assertNotEquals(
                JarDiscovery.cacheProfile(Collections.emptySet(), first),
                JarDiscovery.cacheProfile(Collections.emptySet(), changed));
    }
}
