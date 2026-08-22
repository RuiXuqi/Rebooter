package fermiumbooter.rebooter.discovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModIndexTest {

    @Test
    void unknownAndBlankModIdsAreAbsent() {
        ModIndex index = new ModIndex();

        assertFalse(index.isPresent("jei"));
        assertFalse(index.isPresent(" "));
        assertNull(index.identity("jei"));
    }

    @Test
    void presenceAndIdentityResolveTheSameKeyIgnoringCase() {
        ModIndex index = new ModIndex();
        index.overwrite(ModIdentity.of("OptiFine", "OptiFine", "HD_U_I7"));

        assertTrue(index.isPresent("optifine"));
        assertEquals(ModIdentity.of("OptiFine", "OptiFine", "HD_U_I7"), index.identity("optifine"));
    }

    @Test
    void overwriteReplacesTheKnownIdentityAndKeepsTheRegisteredKey() {
        ModIndex index = new ModIndex();
        index.overwrite(ModIdentity.of("Jei", "Just Enough Items", "4.16.1"));

        index.overwrite(ModIdentity.presenceOnly("jei"));

        assertEquals(ModIdentity.presenceOnly("Jei"), index.identity("jei"));
    }

    @Test
    void addIfAbsentOnlyProvesPresence() {
        ModIndex index = new ModIndex();
        index.overwrite(ModIdentity.of("optifine", "OptiFine", "HD_U_I7"));

        index.addIfAbsent("optifine");
        index.addIfAbsent("essential");

        assertEquals(ModIdentity.of("optifine", "OptiFine", "HD_U_I7"), index.identity("optifine"));
        assertEquals(ModIdentity.presenceOnly("essential"), index.identity("essential"));
    }

    @Test
    void fillDeclaredKeepsWhatItDoesNotDeclare() {
        ModIndex index = new ModIndex();
        index.overwrite(ModIdentity.of("jei", "Just Enough Items", "4.16.1"));

        index.fillDeclared(ModIdentity.of("jei", "Just Enough Items Fork", null));

        assertEquals(ModIdentity.of("jei", "Just Enough Items Fork", "4.16.1"), index.identity("jei"));
    }

    @Test
    void fillDeclaredRegistersAnUnknownMod() {
        ModIndex index = new ModIndex();

        index.fillDeclared(ModIdentity.of("jei", "Just Enough Items", "4.16.1"));

        assertEquals(ModIdentity.of("jei", "Just Enough Items", "4.16.1"), index.identity("jei"));
    }

    @Test
    void clearForgetsEverything() {
        ModIndex index = new ModIndex();
        index.overwrite(ModIdentity.presenceOnly("jei"));

        index.clear();

        assertFalse(index.isPresent("jei"));
        assertTrue(index.snapshot().isEmpty());
    }
}
