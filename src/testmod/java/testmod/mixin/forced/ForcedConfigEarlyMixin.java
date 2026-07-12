package testmod.mixin.forced;

import net.minecraftforge.fml.common.LoadController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import testmod.verification.RuntimeProbe;

@Mixin(value = LoadController.class, remap = false)
public class ForcedConfigEarlyMixin {

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void rebooterTestmod$forcedConfigEarly(CallbackInfo callbackInfo) {
        RuntimeProbe.mark(RuntimeProbe.Case.FORCED_CONFIG_EARLY);
    }
}
