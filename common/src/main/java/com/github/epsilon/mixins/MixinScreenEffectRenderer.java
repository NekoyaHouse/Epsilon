package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import com.github.epsilon.modules.impl.render.TotemAnimation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class MixinScreenEffectRenderer {

    @Shadow
    private ItemStack itemActivationItem;

    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    private static void onRenderFluid(TextureAtlasSprite texture, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.blockOverlay.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    private static void onRenderWater(CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.liquidOverlay.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void onRenderFire(PoseStack poseStack, MultiBufferSource bufferSource, TextureAtlasSprite texture, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.fireOverlay.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void displayTotemAnimation(ItemStack itemStack, RandomSource random, CallbackInfo ci) {
        TotemAnimation totemAnimation = TotemAnimation.INSTANCE;
        if (totemAnimation.isEnabled()) {
            totemAnimation.showFloatingItem(itemStack);
            ci.cancel();
        }
    }

    @WrapOperation(method = "renderScreenEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;renderItemActivationAnimation(Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void renderTotemAnimation(ScreenEffectRenderer renderer, PoseStack poseStack, float partialTick, SubmitNodeCollector submitNodeCollector, Operation<Void> original) {
        TotemAnimation totemAnimation = TotemAnimation.INSTANCE;
        if (totemAnimation.isEnabled()) {
            totemAnimation.renderFloatingItem(poseStack, partialTick, submitNodeCollector);
        } else {
            original.call(renderer, poseStack, partialTick, submitNodeCollector);
        }
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void onRenderItemActivationAnimation(CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.totemAnimation.getValue() && itemActivationItem != null && itemActivationItem.is(Items.TOTEM_OF_UNDYING)) {
            ci.cancel();
        }
    }

}
