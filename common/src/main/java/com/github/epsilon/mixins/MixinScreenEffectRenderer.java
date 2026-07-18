package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class MixinScreenEffectRenderer {

    @Shadow
    private @Nullable ItemStack itemActivationItem;

    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    private static void onRenderFluid(TextureAtlasSprite texture, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.blockOverlay.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void onRenderFire(PoseStack poseStack, MultiBufferSource bufferSource, TextureAtlasSprite texture, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.fireOverlay.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void onRenderItemActivationAnimation(PoseStack poseStack, float partialTick, SubmitNodeCollector nodeCollector, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled()
                && NoRender.INSTANCE.totemAnimation.getValue()
                && itemActivationItem != null
                && itemActivationItem.is(Items.TOTEM_OF_UNDYING)) {
            ci.cancel();
        }
    }

}
