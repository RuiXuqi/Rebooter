package fermiumbooter.rebooter;

import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.List;

public final class RebooterLateLoader implements ILateMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        return MixinRegistry.handoffLate();
    }
}
