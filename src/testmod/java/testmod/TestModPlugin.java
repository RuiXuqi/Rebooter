package testmod;

import fermiumbooter.FermiumRegistryAPI;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import testmod.fixture.annotation.legacy.LegacyAnnotationFixture;
import testmod.fixture.registry.RegistryFixture;

import javax.annotation.Nullable;
import java.util.Map;

@IFMLLoadingPlugin.MCVersion("1.12.2")
public class TestModPlugin implements IFMLLoadingPlugin {

    static {
        RegistryFixture.register();
        if (!FermiumRegistryAPI.isModPresent(TestMod.MOD_ID)) {
            throw new AssertionError("Early discovery did not find the test mod ID");
        }
        RegistryFixture.verifyNotEvaluatedDuringDiscovery();
        LegacyAnnotationFixture.register();
    }

    @Nullable
    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Nullable
    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
