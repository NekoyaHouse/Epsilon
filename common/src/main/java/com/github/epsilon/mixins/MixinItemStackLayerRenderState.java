package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.Chams;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public class MixinItemStackLayerRenderState {

    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"))
    private void applyChamsRenderType(SubmitNodeCollector submitNodeCollector, PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers, List<BakedQuad> quads, ItemStackRenderState.FoilType foilType, Operation<Void> original) {
        Chams chamsModule = Chams.INSTANCE;
        if (chamsModule.shouldApplyThirdPersonHandItemRenderType()) {
            quads = chamsModule.withChamsRenderType(quads, chamsModule);
        }

        original.call(submitNodeCollector, poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, quads, foilType);
    }

}
