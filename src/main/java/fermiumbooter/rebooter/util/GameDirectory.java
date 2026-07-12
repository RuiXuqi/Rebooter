package fermiumbooter.rebooter.util;

import fermiumbooter.rebooter.Reference;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.apache.commons.lang3.StringUtils;

import java.io.File;

public final class GameDirectory {

    private GameDirectory() {
    }

    public static File resolve() {
        String override = System.getProperty("rebooter.gameDir");
        if (StringUtils.isNotBlank(override)) return new File(override);
        try {
            Object[] injectionData = FMLInjectionData.data();
            if (injectionData.length > 6 && injectionData[6] instanceof File) {
                return (File) injectionData[6];
            }
        } catch (RuntimeException | LinkageError e) {
            Reference.LOGGER.debug("FML game directory is not available during early startup", e);
        }
        return new File(".").getAbsoluteFile();
    }
}
