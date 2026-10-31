package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import com.github.epsilon.modules.impl.render.TotemAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class MixinScreenEffectRenderer {

    // 26.3 的屏幕效果提交改为直接传图集位置与 UV，参数列表随之变化。
    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    private static void onRenderBlockOverlay(Identifier atlasLocation, float u0, float v0, float u1, float v1, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int color, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.getBlockOverlay()) {
            ci.cancel();
        }
    }

    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void onRenderWater(PlayerRenderState.WaterOverlay waterOverlay, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.getLiquidOverlay()) {
            ci.cancel();
        }
    }

    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void onRenderFire(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, TextureAtlasSprite texture, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.getFireOverlay()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void onRenderItemActivationAnimation(PlayerRenderState playerState, PoseStack poseStack, float partialTicks, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        if (TotemAnimation.INSTANCE.isEnabled()) {
            TotemAnimation.INSTANCE.renderFloatingItem(partialTicks, poseStack, submitNodeCollector);
            ci.cancel();
        } else if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.getTotemAnimation()) {
            ci.cancel();
        }
    }

}
