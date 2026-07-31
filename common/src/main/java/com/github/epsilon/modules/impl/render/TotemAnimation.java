package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class TotemAnimation extends Module {

    public static final TotemAnimation INSTANCE = new TotemAnimation();

    private TotemAnimation() {
        super("Totem Animation", Category.RENDER);
    }

    private enum Mode {
        FadeOut,
        Size,
        Otkisuli,
        Insert,
        Fall,
        Rocket,
        Roll,
        Off
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.FadeOut);
    private final IntSetting speed = intSetting("Speed", 40, 1, 100, 1);

    private ItemStack floatingItem;
    private int floatingItemTimeLeft;

    @Override
    protected void onDisable() {
        floatingItem = null;
        floatingItemTimeLeft = 0;
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (floatingItemTimeLeft > 0) {
            --floatingItemTimeLeft;
            if (floatingItemTimeLeft == 0) {
                floatingItem = null;
            }
        }
    }

    public void showFloatingItem(ItemStack itemStack) {
        floatingItem = itemStack;
        floatingItemTimeLeft = getTime();
    }

    public void renderFloatingItem(PoseStack poseStack, float partialTick, SubmitNodeCollector submitNodeCollector) {
        if (floatingItem == null || floatingItemTimeLeft <= 0 || mode.is(Mode.Off)) return;

        int animationTime = getTime();
        float elapsedTime = animationTime - floatingItemTimeLeft;
        float animationProgress = (elapsedTime + partialTick) / animationTime;
        float progressSquared = animationProgress * animationProgress;
        float progressCubed = animationProgress * progressSquared;
        float oscillationFactor = 10.25F * progressCubed * progressSquared - 24.95F * progressSquared * progressSquared + 25.5F * progressCubed - 13.8F * progressSquared + 4.0F * animationProgress;
        float oscillationRadians = oscillationFactor * (float) Math.PI;
        float adjustedProgress = elapsedTime + partialTick;
        float scale = 50.0F + 175.0F * (float) Math.sin(oscillationRadians);

        poseStack.pushPose();
        switch (mode.getValue()) {
            case FadeOut -> {
                float x = (float) Math.sin(adjustedProgress * 112.0F / 180.0F) * 100.0F;
                float y = (float) Math.cos(adjustedProgress * 112.0F / 180.0F) * 50.0F;
                translateFromLegacyGui(poseStack, x, y);
                poseStack.scale(scale / 280.0f, scale / 280.0f, scale / 280.0f);
            }
            case Size -> {
                poseStack.translate(0.0F, 0.0F, -10.0F);
                poseStack.scale(scale / 280.0f, scale / 280.0f, scale / 280.0f);
            }
            case Otkisuli -> {
                poseStack.translate(0.0F, 0.0F, -10.0F);
                poseStack.mulPose(Axis.XP.rotationDegrees(adjustedProgress * 2.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(adjustedProgress * 2.0F));
                scaleLegacy(poseStack, 200.0F - adjustedProgress * 1.5F);
            }
            case Insert -> {
                poseStack.translate(0.0F, 0.0F, -10.0F);
                poseStack.mulPose(Axis.XP.rotationDegrees(adjustedProgress * 3.0F));
                scaleLegacy(poseStack, 200.0F - adjustedProgress * 1.5F);
            }
            case Fall -> {
                float downFactor = (float) Math.pow(adjustedProgress, 3) * 0.2F;
                translateFromLegacyGui(poseStack, 0.0F, downFactor);
                poseStack.mulPose(Axis.ZP.rotationDegrees(adjustedProgress * 5.0F));
                scaleLegacy(poseStack, 200.0F - adjustedProgress * 1.5F);
            }
            case Rocket -> {
                float downFactor = (float) Math.pow(adjustedProgress, 3) * 0.2F - 20.0F;
                translateFromLegacyGui(poseStack, 0.0F, -downFactor);
                poseStack.mulPose(Axis.YP.rotationDegrees(adjustedProgress * floatingItemTimeLeft * 2.0F));
                scaleLegacy(poseStack, 200.0F - adjustedProgress * 1.5F);
            }
            case Roll -> {
                float rightFactor = (float) Math.pow(adjustedProgress, 2) * 4.5F;
                translateFromLegacyGui(poseStack, rightFactor, 0.0F);
                poseStack.mulPose(Axis.ZP.rotationDegrees(adjustedProgress * 40.0F));
                scaleLegacy(poseStack, 200.0F - adjustedProgress * 1.5F);
            }
            case Off -> {
            }
        }

        mc.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
        ItemStackRenderState itemState = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(itemState, floatingItem, ItemDisplayContext.FIXED, mc.level, null, 0);
        itemState.submit(poseStack, submitNodeCollector, 15728880, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    private void translateFromLegacyGui(PoseStack poseStack, float x, float y) {
        float guiHeight = mc.getWindow().getGuiScaledHeight();
        poseStack.translate(x * 2.0F / guiHeight, -y * 2.0F / guiHeight, -10.0F);
    }

    private void scaleLegacy(PoseStack poseStack, float legacyScale) {
        float scale = legacyScale / 280.0f;
        poseStack.scale(scale, scale, scale);
    }

    private int getTime() {
        int invertedSpeed = 101 - speed.getValue();
        if (mode.is(Mode.FadeOut)) return invertedSpeed / 4;
        if (mode.is(Mode.Insert)) return invertedSpeed / 2;
        return invertedSpeed;
    }

}
