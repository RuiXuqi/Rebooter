package fermiumbooter;

import fermiumbooter.rebooter.RebooterCorePlugin;
import fermiumbooter.rebooter.RebooterLateLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FermiumRegistryAPITest {

    @TempDir
    Path gameDirectory;

    @Test
    void registryPreservesEarlyLateOrderingAndRejectionSemantics() throws Exception {
        ForgeTestEnvironment.setGameDirectory(this.gameDirectory);
        AtomicInteger evaluations = new AtomicInteger();
        AtomicInteger rejectedEvaluations = new AtomicInteger();
        FermiumRegistryAPI.enqueueMixin(false, "mixins.first.json", () -> {
            evaluations.incrementAndGet();
            return false;
        });
        FermiumRegistryAPI.enqueueMixin(false, "mixins.first.json", () -> {
            evaluations.incrementAndGet();
            return true;
        });
        FermiumRegistryAPI.enqueueMixin(false, "mixins.rejected.json", true);
        FermiumRegistryAPI.enqueueMixin(false, "mixins.rejected-throwing.json", () -> {
            rejectedEvaluations.incrementAndGet();
            throw new IllegalStateException("rejected supplier must not run");
        });
        FermiumRegistryAPI.enqueueMixin(true, "mixins.late.json", true);
        FermiumRegistryAPI.enqueueMixin(false, "mixins.second.json", "mixins.third.json");
        FermiumRegistryAPI.enqueueMixin(false, "   ", true);
        FermiumRegistryAPI.enqueueMixin(false, "mixins.null-supplier.json", null);
        FermiumRegistryAPI.removeMixin("mixins.rejected.json");
        FermiumRegistryAPI.removeMixin("mixins.rejected.json");
        FermiumRegistryAPI.removeMixin("mixins.rejected-throwing.json");

        List<String> early = new RebooterCorePlugin().getMixinConfigs();

        assertEquals(
                Arrays.asList(
                        "mixins.fermiumbooter.init.json",
                        "mixins.first.json",
                        "mixins.second.json",
                        "mixins.third.json"),
                early);
        assertEquals(2, evaluations.get(), "every duplicate supplier must be evaluated");
        assertEquals(0, rejectedEvaluations.get(), "rejected suppliers must be skipped before evaluation");
        assertFalse(early.contains("mixins.rejected.json"));

        List<String> late = new RebooterLateLoader().getMixinConfigs();
        assertEquals(Collections.singletonList("mixins.late.json"), late);
        assertEquals(Collections.emptyList(), new RebooterLateLoader().getMixinConfigs());
    }
}
