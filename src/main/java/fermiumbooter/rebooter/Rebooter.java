package fermiumbooter.rebooter;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;

@Mod(
        modid = Reference.MOD_ID,
        name = Reference.MOD_NAME,
        version = Reference.COMPAT_VERSION,
        dependencies = "required-after:mixinbooter@[8.0,);",
        acceptableRemoteVersions = "*",
        customProperties = {
                @Mod.CustomProperty(k = "license", v = "The Unlicense"),
                @Mod.CustomProperty(k = "issueTrackerUrl", v = "https://github.com/RuiXuqi/Rebooter/issues")
        }
)
public final class Rebooter {
    @Mod.EventHandler
    public void preInit(FMLPostInitializationEvent event) {
        NetworkVersion.applyConfigured();
    }
}
