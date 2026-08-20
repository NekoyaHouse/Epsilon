package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 1))
    private <S extends LivingEntityRenderState> void addOldWalkAnimation(S state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, CallbackInfo ci) {
        if (ClientSetting.INSTANCE.oldWalkingAnimation.isEnabled()) {
            final float limbSwingAnimationProgress = state.walkAnimationPos;
            final float limbSwingAmplitude = state.walkAnimationSpeed;

            poseStack.translate(0.0F, -Math.abs(Math.cos(limbSwingAnimationProgress * 0.6662F)) * 0.3125F * limbSwingAmplitude, 0.0F);
        }
    }

}
