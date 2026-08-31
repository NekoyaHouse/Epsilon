package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.Chams;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SubmitNodeCollection.class)
public abstract class MixinSubmitNodeCollection {

    @Shadow
    @Final
    public SimpleFeatureRenderPhase alwaysOnTop;

    @Inject(method = "submitModel", at = @At("HEAD"), cancellable = true)
    private <S> void submitChamsModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords, int tintedColor, TextureAtlasSprite sprite, int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, CallbackInfo ci) {
        Chams chams = Chams.INSTANCE;
        if ((chams.isSubmittingPlayer() || chams.isChamsRenderType(renderType)) && !renderType.isOutline()) {
            this.alwaysOnTop.submit(new ModelFeatureRenderer.Submit<>(renderType, poseStack.last().copy(), model, state, lightCoords, overlayCoords, tintedColor, sprite, null));
            ci.cancel();
        }
    }

    @Inject(method = "submitItem", at = @At("HEAD"), cancellable = true)
    private void submitChamsItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers, List<BakedQuad> quads, ItemStackRenderState.FoilType foilType, CallbackInfo ci) {
        if (Chams.INSTANCE.isSubmittingPlayer()) {
            this.alwaysOnTop.submit(new ItemFeatureRenderer.Submit(poseStack.last().copy(), displayContext, lightCoords, overlayCoords, 0, tintLayers, quads, foilType));
            ci.cancel();
        }
    }

    @Inject(method = "submitCustomGeometry", at = @At("HEAD"), cancellable = true)
    private void submitChamsCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer, CallbackInfo ci) {
        if (Chams.INSTANCE.isSubmittingPlayer() && !renderType.isOutline()) {
            this.alwaysOnTop.submit(new CustomFeatureRenderer.Submit(poseStack.last().copy(), renderType, customGeometryRenderer));
            ci.cancel();
        }
    }

}
