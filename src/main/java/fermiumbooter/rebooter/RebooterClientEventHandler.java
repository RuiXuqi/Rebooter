package fermiumbooter.rebooter;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class RebooterClientEventHandler {
    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (Reference.MOD_ID.equals(event.getModID())) {
            ConfigManager.sync(Reference.MOD_ID, Config.Type.INSTANCE);
            NetworkVersion.applyConfigured();
        }
    }
}
