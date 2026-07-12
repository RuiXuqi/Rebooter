package testmod.mixin.registry;

import net.minecraftforge.fml.common.LoadController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import testmod.verification.RuntimeProbe;

@Mixin(value = LoadController.class, remap = false)
public class RegistryEarlyMixin {

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void rebooterTestmod$registryEarly(CallbackInfo callbackInfo) {
        RuntimeProbe.mark(RuntimeProbe.Case.REGISTRY_EARLY);
    }
}
