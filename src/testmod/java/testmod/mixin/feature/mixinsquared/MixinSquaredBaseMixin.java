package testmod.mixin.feature.mixinsquared;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import testmod.TestMod;

@Mixin(value = TestMod.class, remap = false)
public class MixinSquaredBaseMixin {

    @Inject(method = "transformedValue", at = @At("HEAD"), cancellable = true, remap = false)
    private void rebooterTestmod$base(CallbackInfoReturnable<String> callbackInfo) {
        callbackInfo.setReturnValue("base-mixin");
    }
}
