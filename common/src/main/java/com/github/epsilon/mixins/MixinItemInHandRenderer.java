package com.github.epsilon.mixins;

import com.github.epsilon.holders.ShaderHolder;
import com.github.epsilon.modules.impl.render.HandsView;
import com.github.epsilon.modules.impl.render.Shaders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.github.epsilon.Constants.mc;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {

    @Unique
    private boolean epsilon$blocked;

    @Shadow
    protected abstract void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float attackValue);

    @Final
    @Shadow
    private Minecraft minecraft;

    @Shadow
    public float mainHandHeight;

    @Shadow
    public float offHandHeight;

    @Shadow
    public ItemStack mainHandItem;

    @Shadow
    public ItemStack offHandItem;

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void beginShadersHandCapture(float frameInterp, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, LocalPlayer player, int lightCoords, CallbackInfo ci) {
        Shaders shaders = Shaders.INSTANCE;
        if (shaders.isEnabled() && shaders.shouldRenderHands()) {
            ShaderHolder.INSTANCE.beginHandOutlineCapture(mc.getMainRenderTarget().width, mc.getMainRenderTarget().height);
        }
    }

    @Inject(method = "renderHandsWithItems", at = @At("RETURN"))
    private void endShadersHandCapture(float frameInterp, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, LocalPlayer player, int lightCoords, CallbackInfo ci) {
        if (ShaderHolder.INSTANCE.isRenderingHands()) {
            minecraft.renderBuffers().outlineBufferSource().endOutlineBatch();
        }
        ShaderHolder.INSTANCE.endHandOutlineCapture();
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void cacheBlockingState(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        epsilon$blocked = HandsView.INSTANCE.shouldApplyBlockingAnimation(hand, itemStack);
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void clearBlockingState(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        epsilon$blocked = false;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void hideHotbarSwitchAnimation(CallbackInfo ci) {
        HandsView handsView = HandsView.INSTANCE;

        boolean shouldMainHide = handsView.isEnabled() && handsView.disableSwapMain.getValue();
        if (shouldMainHide) {
            mainHandHeight = 1.0f;
            mainHandItem = minecraft.player.getMainHandItem();
        }

        boolean shouldOffHide = handsView.isEnabled() && handsView.disableSwapOff.getValue();
        if (shouldOffHide) {
            offHandHeight = 1.0f;
            offHandItem = minecraft.player.getOffhandItem();
        }
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V", ordinal = 2, shift = At.Shift.AFTER))
    private void addSwingToEating(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        HandsView handsView = HandsView.INSTANCE;
        if (handsView.isEnabled() && handsView.swingWhileUsing.getValue() && attack > 0.0F) {
            HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
            applyItemArmAttackTransform(poseStack, arm, attack);
        }
    }

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void cancelSwingForBlocking(float attack, PoseStack poseStack, int invert, HumanoidArm arm, CallbackInfo ci) {
        if (epsilon$blocked) ci.cancel();
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V", ordinal = 1, shift = At.Shift.BEFORE))
    private void applyBlockingAnimation(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        if (!epsilon$blocked) return;
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        HandsView.INSTANCE.applyBlockingTransform(poseStack, arm, attack);
    }

    @WrapOperation(method = "swingArm", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void wrapSwingArmTranslate(PoseStack poseStack, float xo, float yo, float zo, Operation<Void> original) {
        HandsView handsView = HandsView.INSTANCE;
        boolean skip = handsView.isEnabled() && handsView.swingMode.is(HandsView.SwingMode.Flux);

        if (skip && handsView.onlyWeapon.getValue()) {
            ItemStack item = mc.player.getMainHandItem();
            if (!item.is(ItemTags.SWORDS) && !item.is(ItemTags.AXES)) {
                skip = false;
            }
        }

        if (!skip) {
            original.call(poseStack, xo, yo, zo);
        }
    }

    @ModifyArg(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"), index = 4)
    private int applyShadersHandOutline(int outlineColor) {
        Shaders shaders = Shaders.INSTANCE;
        return shaders.isEnabled() && shaders.shouldRenderHands() ? shaders.outlineColor.getValue().getRGB() : outlineColor;
    }

}
