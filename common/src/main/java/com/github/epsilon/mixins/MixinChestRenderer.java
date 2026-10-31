package com.github.epsilon.mixins;

import com.github.epsilon.managers.ShaderManager;
import com.github.epsilon.modules.impl.render.Chams;
import com.github.epsilon.modules.impl.render.Shaders;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChestRenderer.class)
public class MixinChestRenderer {

    @Redirect(method = "submit*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;IIILnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;I)V"))
    private <S> void applyShadersChestOutline(SubmitNodeCollector submitNodeCollector, Model<S> model, S modelState, PoseStack poseStack, int lightCoords, int overlayCoords, int tintedColor, SpriteId sprite, SpriteGetter sprites, int outlineColor, ChestRenderState state) {
        Shaders shaders = Shaders.INSTANCE;
        boolean renderShaderOutline = shaders.isEnabled() && shaders.shouldRenderChest(state.blockPos);
        int finalOutlineColor = renderShaderOutline ? 0 : outlineColor;
        Chams chams = Chams.INSTANCE;
        if (chams.isEnabled() && chams.getChests()) {
            RenderType renderType = chams.getRenderType(sprite.atlasLocation());
            submitNodeCollector.submitModel(model, modelState, poseStack, renderType, lightCoords, overlayCoords, tintedColor, sprites.get(sprite), finalOutlineColor);
        } else {
            submitNodeCollector.submitModel(model, modelState, poseStack, lightCoords, overlayCoords, tintedColor, sprite, sprites, finalOutlineColor);
        }

        if (renderShaderOutline) {
            // 26.3 的 RenderSetup 不再携带输出目标：胸箱描边需要提交到模块自己的缓存，
            // 再由 ShaderManager 渲染进 chestTarget。
            int tint = shaders.getOutlineColor(shaders.getChestShader());
            RenderType outlineRenderType = ShaderManager.INSTANCE.prepareChestOutline(sprite.atlasLocation());
            ShaderManager.INSTANCE.chestOutlineCollector()
                    .submitModel(model, modelState, poseStack, outlineRenderType, lightCoords, overlayCoords, tint, sprites.get(sprite), tint);
        }
    }

}
