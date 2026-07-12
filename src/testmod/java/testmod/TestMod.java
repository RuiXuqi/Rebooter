package testmod;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import testmod.verification.RuntimeVerification;

@Mod(
        modid = TestMod.MOD_ID,
        name = "Rebooter Test Mod",
        version = "1.0.0"
)
public class TestMod {
    public static final String MOD_ID = "testmod";

    public String transformedValue() {
        return "base";
    }

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        RuntimeVerification.verify();
    }
}
