package com.github.epsilon.neoforge.mixins;

import com.github.epsilon.modules.impl.render.FreeCamera;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public class MixinCamera {

    @ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FFF)V"))
    private void onAlignSetRotationArgs(Args args, @Local(argsOnly = true, name = "partialTicks") float partialTicks) {
        FreeCamera freeCamera = FreeCamera.INSTANCE;
        if (freeCamera.isEnabled()) {
            args.set(0, (float) freeCamera.getYaw(partialTicks));
            args.set(1, (float) freeCamera.getPitch(partialTicks));
        }
    }

}
