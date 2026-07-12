package fermiumbooter.rebooter;

import fermiumbooter.config.FermiumBooterConfig;
import net.minecraftforge.fml.common.FMLModContainer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.lang.reflect.Field;

public final class NetworkVersion {
    private static Field internalVersion;
    private static boolean failed;

    private NetworkVersion() {
    }

    static void applyConfigured() {
        if (!FermiumBooterConfig.enableCustomNetworkVersion) {
            return;
        }
        ModContainer container = Loader.instance().getIndexedModList().get(Reference.MOD_ID);
        if (container instanceof FMLModContainer) {
            apply((FMLModContainer) container, FermiumBooterConfig.customNetworkVersion);
        } else {
            Reference.LOGGER.error("Missing FMLModContainer for network version");
        }
    }

    private static void apply(FMLModContainer container, String networkVersion) {
        if (failed) {
            return;
        }
        if (internalVersion == null) {
            try {
                Field field = FMLModContainer.class.getDeclaredField("internalVersion");
                field.setAccessible(true);
                internalVersion = field;
            } catch (ReflectiveOperationException | RuntimeException e) {
                Reference.LOGGER.error("Failed to access FMLModContainer.internalVersion", e);
                failed = true;
                return;
            }
        }
        try {
            internalVersion.set(container, networkVersion);
        } catch (IllegalAccessException | RuntimeException e) {
            Reference.LOGGER.error("Failed to set network version to {}", networkVersion, e);
        }
    }
}
