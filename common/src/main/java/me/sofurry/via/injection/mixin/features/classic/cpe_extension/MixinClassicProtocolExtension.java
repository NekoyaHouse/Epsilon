package me.sofurry.via.injection.mixin.features.classic.cpe_extension;

import me.sofurry.via.features.classic.cpe_extension.CPEAdditions;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.data.ClassicProtocolExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClassicProtocolExtension.class, remap = false)
public abstract class MixinClassicProtocolExtension {

    @Inject(method = "supportsVersion", at = @At("HEAD"), cancellable = true)
    private void allowExtensions_supportsVersion(int version, CallbackInfoReturnable<Boolean> cir) {
        if (CPEAdditions.ALLOWED_EXTENSIONS.contains((ClassicProtocolExtension) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isSupported", at = @At("HEAD"), cancellable = true)
    private void allowExtensions_isSupported(CallbackInfoReturnable<Boolean> cir) {
        if (CPEAdditions.ALLOWED_EXTENSIONS.contains((ClassicProtocolExtension) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getHighestSupportedVersion", at = @At("HEAD"), cancellable = true)
    private void allowExtensions_getHighestSupportedVersion(CallbackInfoReturnable<Integer> cir) {
        if (CPEAdditions.ALLOWED_EXTENSIONS.contains((ClassicProtocolExtension) (Object) this)) {
            cir.setReturnValue(1);
        }
    }

}
