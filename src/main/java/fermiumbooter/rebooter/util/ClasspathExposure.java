package fermiumbooter.rebooter.util;

import fermiumbooter.rebooter.Reference;
import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Exposes jars that declare discovered Mixin toggles to the launch classpath. Ordinary mod jars
 * without a coremod are otherwise invisible to the resource lookup used when Mixin resolves
 * configuration names, so the jar containing the configuration must be reachable there.
 */
public final class ClasspathExposure {

    private ClasspathExposure() {
    }

    public static void expose(Collection<File> jars) {
        if (jars.isEmpty() || Launch.classLoader == null) {
            return;
        }
        Set<String> known = new HashSet<>();
        for (URL url : Launch.classLoader.getURLs()) {
            known.add(normalize(url));
        }
        for (File jar : jars) {
            try {
                URI uri = jar.getCanonicalFile().toURI();
                if (!known.add(normalize(uri.toURL()))) {
                    continue;
                }
                Launch.classLoader.addURL(uri.toURL());
                Reference.LOGGER.debug("Exposed {} on the launch classpath for Mixin resource loading", jar.getName());
            } catch (Exception e) {
                Reference.LOGGER.error("Failed to expose {} on the launch classpath", jar.getName(), e);
            }
        }
    }

    private static String normalize(URL url) {
        return url.toExternalForm().toLowerCase(Locale.ROOT);
    }
}
