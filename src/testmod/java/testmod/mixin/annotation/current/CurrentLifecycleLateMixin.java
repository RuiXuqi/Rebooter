package testmod.mixin.annotation.current;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import testmod.TestMod;
import testmod.verification.RuntimeProbe;

@Mixin(value = TestMod.class, remap = false)
public class CurrentLifecycleLateMixin {

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void rebooterTestmod$currentLifecycleLate(CallbackInfo callbackInfo) {
        RuntimeProbe.mark(RuntimeProbe.Case.CURRENT_LIFECYCLE_LATE);
    }
}
