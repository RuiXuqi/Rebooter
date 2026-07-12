package fermiumbooter;

import fermiumbooter.rebooter.RebooterCorePlugin;
import fermiumbooter.rebooter.RebooterLateLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RegistryEdgeCaseTest {

    @TempDir
    Path gameDirectory;

    @Test
    void nullResultsAreFalseAndSupplierExceptionsPropagateToMixinBooter() throws Exception {
        ForgeTestEnvironment.setGameDirectory(this.gameDirectory);
        AtomicInteger laterSupplier = new AtomicInteger();
        FermiumRegistryAPI.enqueueMixin(false, "mixins.null-result.json", () -> null);
        FermiumRegistryAPI.enqueueMixin(false, "mixins.null-result.json", () -> {
            laterSupplier.incrementAndGet();
            return false;
        });

        assertFalse(new RebooterCorePlugin().getMixinConfigs().contains("mixins.null-result.json"));
        assertEquals(1, laterSupplier.get(), "all suppliers must run after a null result");

        FermiumRegistryAPI.enqueueMixin(true, "mixins.throwing.json", () -> {
            throw new IllegalStateException("expected fixture failure");
        });
        assertThrows(
                IllegalStateException.class,
                () -> new RebooterLateLoader().getMixinConfigs());
    }

    @Test
    void builtInModIdsAreCaseInsensitive() {
        assertTrue(FermiumRegistryAPI.isModPresent("minecraft"));
        assertTrue(FermiumRegistryAPI.isModPresent("mcp"));
        assertTrue(FermiumRegistryAPI.isModPresent("FML"));
        assertTrue(FermiumRegistryAPI.isModPresent("fml"));
        assertTrue(FermiumRegistryAPI.isModPresent("forge"));
        assertTrue(FermiumRegistryAPI.isModPresent("FORGE"));
        assertFalse(FermiumRegistryAPI.isModPresent(null));
        assertFalse(FermiumRegistryAPI.isModPresent(""));
    }
}
