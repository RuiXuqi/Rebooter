package testmod.fixture.registry;

import fermiumbooter.FermiumRegistryAPI;
import net.minecraftforge.fml.common.Loader;

import java.util.concurrent.atomic.AtomicInteger;

public final class RegistryFixture {

    private static final AtomicInteger SUPPLIER_EVALUATIONS = new AtomicInteger();

    private RegistryFixture() {
    }

    public static void register() {
        FermiumRegistryAPI.enqueueMixin(
                false, "testmod/mixins/registry/early.json", true);
        FermiumRegistryAPI.enqueueMixin(
                true,
                "testmod/mixins/registry/late.json",
                () -> {
                    SUPPLIER_EVALUATIONS.incrementAndGet();
                    return Loader.isModLoaded("forge");
                });
        FermiumRegistryAPI.removeMixin("testmod/mixins/registry/removed.json");
    }

    public static void verify() {
        if (SUPPLIER_EVALUATIONS.get() != 1) {
            throw new AssertionError("Registry supplier did not execute exactly once");
        }
    }

    public static void verifyNotEvaluatedDuringDiscovery() {
        if (SUPPLIER_EVALUATIONS.get() != 0) {
            throw new AssertionError("Registry supplier ran during early mod discovery");
        }
    }
}
