package com.github.epsilon.mixins;

import com.github.epsilon.holders.ShaderHolder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OutputTarget.class)
public class MixinOutputTarget {

    @Inject(method = "getRenderTarget", at = @At("HEAD"), cancellable = true)
    private void redirectHandOutlineTarget(CallbackInfoReturnable<RenderTarget> cir) {
        if ((OutputTarget) (Object) this == OutputTarget.OUTLINE_TARGET) {
            RenderTarget chestTarget = ShaderHolder.INSTANCE.getChestOutlineTarget();
            if (chestTarget != null) {
                cir.setReturnValue(chestTarget);
                return;
            }

            RenderTarget handTarget = ShaderHolder.INSTANCE.getHandOutlineTarget();
            if (handTarget != null) {
                cir.setReturnValue(handTarget);
            }
        }
    }

}
