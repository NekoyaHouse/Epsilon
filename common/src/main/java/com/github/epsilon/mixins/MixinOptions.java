package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.FreeCamera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public class MixinOptions {

    @Inject(method = "setCameraType", at = @At("HEAD"), cancellable = true)
    private void setPerspective(CameraType cameraType, CallbackInfo ci) {
        if (FreeCamera.INSTANCE.isEnabled()) {
            ci.cancel();
        }
    }

}
