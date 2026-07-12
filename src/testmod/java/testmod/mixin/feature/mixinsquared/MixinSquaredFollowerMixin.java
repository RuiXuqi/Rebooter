package testmod.mixin.feature.mixinsquared;

import com.bawnorton.mixinsquared.TargetHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import testmod.TestMod;

@Mixin(value = TestMod.class, remap = false)
public class MixinSquaredFollowerMixin {

    @TargetHandler(
            mixin = "testmod.mixin.feature.mixinsquared.MixinSquaredBaseMixin",
            name = "rebooterTestmod$base",
            prefix = "handler")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, remap = false)
    private void rebooterTestmod$mixinSquared(
            CallbackInfoReturnable<String> originalCallback, CallbackInfo callbackInfo) {
        originalCallback.setReturnValue("mixin-squared");
        callbackInfo.cancel();
    }
}
