package fermiumbooter.util;

import fermiumbooter.rebooter.discovery.JarDiscovery;

/**
 * Compatibility shim preserving a mod ID lookup entry point from a class that was an internal
 * implementation detail in FermiumBooter. The lookup delegates to Rebooter's discovery service.
 *
 * @since 1.3.0
 */
@SuppressWarnings("unused")
public abstract class FermiumJarScanner {
    /**
     * Returns whether the supplied mod id is known to Rebooter's discovery index. Null or blank ids return
     * {@code false}. The first valid query may synchronously initialize jar discovery.
     *
     * @param modId mod id to query, case-insensitive
     * @return {@code true} when the id is built in, discovered from a mod, or identified through a configured
     * package mapping
     * @see fermiumbooter.FermiumRegistryAPI#isModPresent(String)
     * @since 1.3.0
     */
    public static boolean isModPresent(String modId) {
        return JarDiscovery.isModPresent(modId);
    }
}
