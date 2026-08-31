package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.ArmRenderEvent;
import com.github.epsilon.events.impl.HeldItemRenderEvent;
import com.github.epsilon.managers.ShaderManager;
import com.github.epsilon.modules.impl.render.HandView;
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
import net.minecraft.world.item.ItemUseAnimation;
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
    private float mainHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    private void beginShadersHandCapture(float frameInterp, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, LocalPlayer player, int lightCoords, CallbackInfo ci) {
        Shaders shaders = Shaders.INSTANCE;
        if (shaders.isEnabled() && shaders.hands.getValue()) {
            ShaderManager.INSTANCE.beginHandOutlineCapture(mc.gameRenderer.mainRenderTarget().width, mc.gameRenderer.mainRenderTarget().height);
        }
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void cacheBlockingState(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        epsilon$blocked = HandView.INSTANCE.shouldApplyBlockingAnimation(hand, itemStack);
    }

    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getUseAnimation()Lnet/minecraft/world/item/ItemUseAnimation;"))
    private ItemUseAnimation redirectGetUseAnimation(ItemStack itemStack, Operation<ItemUseAnimation> original) {
        // HandView功能提供完整的防砍动画实现，得抑制翻译物品的原始BLOCK pose
        // 不然进入Legacy服务器会给你的pose修改为BLOCK，导致视觉上看起来双重变换
        ItemUseAnimation useAnimation = original.call(itemStack);
        if (epsilon$blocked) {
            return ItemUseAnimation.NONE;
        }

        // 旧版服务器可以在松开右键单击后保持isUsingItem为true一帧
        if (useAnimation == ItemUseAnimation.BLOCK && !minecraft.options.keyUse.isDown()) {
            return ItemUseAnimation.NONE;
        }
        return useAnimation;
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void clearBlockingState(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        epsilon$blocked = false;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void hideHotbarSwitchAnimation(CallbackInfo ci) {
        HandView handView = HandView.INSTANCE;

        boolean shouldMainHide = handView.isEnabled() && handView.disableSwapMain.getValue();
        if (shouldMainHide) {
            mainHandHeight = 1.0f;
            mainHandItem = minecraft.player.getMainHandItem();
        }

        boolean shouldOffHide = handView.isEnabled() && handView.disableSwapOff.getValue();
        if (shouldOffHide) {
            offHandHeight = 1.0f;
            offHandItem = minecraft.player.getOffhandItem();
        }
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V", ordinal = 2, shift = At.Shift.AFTER))
    private void addSwingToEating(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        HandView handView = HandView.INSTANCE;
        if (handView.isEnabled() && handView.swingWhileUsing.getValue() && attack > 0.0F) {
            HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
            applyItemArmAttackTransform(poseStack, arm, attack);
        }
    }

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void cancelSwingForBlocking(float attack, PoseStack poseStack, int invert, HumanoidArm arm, CallbackInfo ci) {
        if (epsilon$blocked) ci.cancel();
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V", shift = At.Shift.BEFORE))
    private void beforeRenderHeldItem(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        EventBus.INSTANCE.post(new HeldItemRenderEvent(hand, poseStack));
        if (epsilon$blocked) {
            HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
            HandView.INSTANCE.applyBlockingTransform(poseStack, arm, attack, inverseArmHeight);
        }
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFLnet/minecraft/world/entity/HumanoidArm;)V"))
    private void beforeRenderArm(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        EventBus.INSTANCE.post(new ArmRenderEvent(hand, poseStack));
    }

    @WrapOperation(method = "swingArm", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void wrapSwingArmTranslate(PoseStack poseStack, float xo, float yo, float zo, Operation<Void> original) {
        HandView handView = HandView.INSTANCE;
        boolean skip = handView.isEnabled() && handView.swingMode.is(HandView.SwingMode.Flux);

        if (skip && handView.onlyWeapon.getValue()) {
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
        return shaders.isEnabled() && shaders.hands.getValue() ? shaders.getOutlineColor(shaders.handsShader) : outlineColor;
    }

}
