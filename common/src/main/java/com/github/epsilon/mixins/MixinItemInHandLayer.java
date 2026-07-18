package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.EntityRenderStateAccessor;
import com.github.epsilon.modules.impl.render.Chams;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static com.github.epsilon.Constants.mc;

@Mixin(ItemInHandLayer.class)
public class MixinItemInHandLayer {

    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void applyChamsToHeldItem(ItemStackRenderState item, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords, int outlineColor, Operation<Void> original, ArmedEntityRenderState state, ItemStackRenderState itemState, ItemStack itemStack, HumanoidArm arm, PoseStack outerPoseStack, SubmitNodeCollector outerSubmitNodeCollector, int outerLightCoords) {
        Chams chamsModule = Chams.INSTANCE;
        if (chamsModule.isEnabled() && chamsModule.noDepth.getValue() && ((EntityRenderStateAccessor) state).epsilon$getEntity() instanceof Player player && player != mc.player) {
            chamsModule.beginThirdPersonHandItemRender();
            original.call(item, poseStack, submitNodeCollector, lightCoords, overlayCoords, outlineColor);
            chamsModule.endThirdPersonHandItemRender();
        } else {
            original.call(item, poseStack, submitNodeCollector, lightCoords, overlayCoords, outlineColor);
        }
    }

}
