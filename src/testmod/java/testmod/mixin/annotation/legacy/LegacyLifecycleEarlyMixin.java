package testmod.mixin.annotation.legacy;

import net.minecraftforge.fml.common.LoadController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import testmod.verification.RuntimeProbe;

@Mixin(value = LoadController.class, remap = false)
public class LegacyLifecycleEarlyMixin {

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void rebooterTestmod$legacyLifecycleEarly(CallbackInfo callbackInfo) {
        RuntimeProbe.mark(RuntimeProbe.Case.LEGACY_LIFECYCLE_EARLY);
    }
}
