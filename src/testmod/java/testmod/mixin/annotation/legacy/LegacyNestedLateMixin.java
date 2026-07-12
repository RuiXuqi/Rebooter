package testmod.mixin.annotation.legacy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import testmod.TestMod;
import testmod.verification.RuntimeProbe;

@Mixin(value = TestMod.class, remap = false)
public class LegacyNestedLateMixin {

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void rebooterTestmod$legacyNestedLate(CallbackInfo callbackInfo) {
        RuntimeProbe.mark(RuntimeProbe.Case.LEGACY_NESTED_LATE);
    }
}
