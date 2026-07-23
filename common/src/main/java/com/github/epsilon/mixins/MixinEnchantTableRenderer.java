package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EnchantTableRenderer.class)
public abstract class MixinEnchantTableRenderer {

    @WrapWithCondition(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/EnchantTableRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;IIILnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private <S> boolean onRenderBook(SubmitNodeCollector instance, Model<? super S> model, Object state, com.mojang.blaze3d.vertex.PoseStack poseStack, int lightCoords, int overlayCoords, int tintedColor, SpriteId spriteId, SpriteGetter spriteGetter, int outlineColor, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        return !(NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.enchTableBook.getValue());
    }

}
