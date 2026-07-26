package fermiumbooter.rebooter;

import fermiumbooter.config.FermiumBooterConfig;
import fermiumbooter.rebooter.discovery.JarDiscovery;
import fermiumbooter.rebooter.discovery.LegacyConfigRegistrar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.toasts.SystemToast;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class RebooterClientEventHandler {
    private static boolean showed = false;

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (Reference.MOD_ID.equals(event.getModID())) {
            ConfigManager.sync(Reference.MOD_ID, Config.Type.INSTANCE);
            NetworkVersion.applyConfigured();
        }
    }

    @SubscribeEvent
    public static void onLoadingDone(GuiOpenEvent event) {
        if (!showed && event.getGui() instanceof GuiMainMenu) {
            showed = true;
            int warningCount = LegacyConfigRegistrar.getWarningCount() + JarDiscovery.getWarningCount();
            if (FermiumBooterConfig.suppressMixinCompatibilityWarningsRender || warningCount == 0) {
                return;
            }
            Minecraft.getMinecraft().getToastGui().add(new SystemToast(SystemToast.Type.NARRATOR_TOGGLE,
                    new TextComponentTranslation(Reference.MOD_ID + ".toast.compatibilityWarnings.title", warningCount),
                    new TextComponentTranslation(Reference.MOD_ID + ".toast.compatibilityWarnings.description")
            ));
        }
    }
}
