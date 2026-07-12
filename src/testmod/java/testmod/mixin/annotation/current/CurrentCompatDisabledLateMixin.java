package testmod.mixin.annotation.current;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import testmod.TestMod;
import testmod.verification.RuntimeProbe;

@Mixin(value = TestMod.class, remap = false)
public class CurrentCompatDisabledLateMixin {

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void rebooterTestmod$currentCompatDisabledLate(CallbackInfo callbackInfo) {
        RuntimeProbe.mark(RuntimeProbe.Case.CURRENT_COMPAT_DISABLED_LATE);
    }
}
