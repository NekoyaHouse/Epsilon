package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import com.github.epsilon.modules.impl.render.TotemAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class MixinScreenEffectRenderer {

    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void onDisplayItemActivation(ItemStack itemStack, RandomSource random, CallbackInfo ci) {
        if (TotemAnimation.INSTANCE.isEnabled()) {
            TotemAnimation.INSTANCE.showFloatingItem(itemStack);
            ci.cancel();
        }
    }

    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    private static void onRenderBlockOverlay(TextureAtlasSprite texture, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int color, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.blockOverlay.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void onRenderWater(net.minecraft.client.Minecraft minecraft, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.liquidOverlay.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void onRenderFire(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, TextureAtlasSprite texture, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.fireOverlay.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void onRenderItemActivationAnimation(PoseStack poseStack, float partialTicks, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        if (TotemAnimation.INSTANCE.isEnabled()) {
            TotemAnimation.INSTANCE.renderFloatingItem(partialTicks, poseStack, submitNodeCollector);
            ci.cancel();
        } else if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.totemAnimation.getValue()) {
            ci.cancel();
        }
    }

}
