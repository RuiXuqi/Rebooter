package fermiumbooter.rebooter.discovery;

import fermiumbooter.rebooter.Reference;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.libraries.Artifact;
import net.minecraftforge.fml.relauncher.libraries.LibraryManager;
import net.minecraftforge.fml.relauncher.libraries.Repository;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class JarCollector {

    private JarCollector() {
    }

    static Set<File> collect(File gameDirectory) {
        Set<File> candidates = new LinkedHashSet<>();
        for (File candidate : LibraryManager.gatherLegacyCanidates(gameDirectory)) {
            addCandidate(candidates, candidate);
        }

        for (Artifact artifact : LibraryManager.flattenLists(gameDirectory)) {
            Artifact resolved = Repository.resolveAll(artifact);
            if (resolved != null) {
                addCandidate(candidates, resolved.getFile());
            }
        }

        if (FMLLaunchHandler.isDeobfuscatedEnvironment()) {
            String classPath = System.getProperty("java.class.path", "");
            for (String entry : classPath.split(Pattern.quote(File.pathSeparator))) {
                File file = new File(entry);
                if (file.isFile() && entry.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    addCandidate(candidates, file);
                }
            }
        }

        return candidates;
    }

    private static void addCandidate(Set<File> candidates, File candidate) {
        try {
            candidates.add(candidate.getCanonicalFile());
        } catch (IOException e) {
            Reference.LOGGER.debug("Using normalized absolute path for discovery candidate {}", candidate, e);
            candidates.add(candidate.getAbsoluteFile().toPath().normalize().toFile());
        }
    }
}
