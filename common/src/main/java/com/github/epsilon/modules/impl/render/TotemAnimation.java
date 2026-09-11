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
import net.minecraft.util.Mth;
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
        Roll
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
        if (floatingItemTimeLeft > 0 && --floatingItemTimeLeft == 0) {
            floatingItem = null;
        }
    }

    public void showFloatingItem(ItemStack item) {
        this.floatingItem = item;
        this.floatingItemTimeLeft = getTime();
    }

    public void renderFloatingItem(float tickDelta, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        if (floatingItem == null || floatingItemTimeLeft <= 0) return;

        int elapsedTime = getTime() - floatingItemTimeLeft;
        float animationProgress = (elapsedTime + tickDelta) / (float) getTime();
        float progressSquared = animationProgress * animationProgress;
        float progressCubed = animationProgress * progressSquared;
        float oscillationFactor = 10.25f * progressCubed * progressSquared
                - 24.95f * progressSquared * progressSquared
                + 25.5f * progressCubed
                - 13.8f * progressSquared
                + 4.0f * animationProgress;
        float oscillationRadians = oscillationFactor * (float) Math.PI;
        float adjustedProgress = elapsedTime + tickDelta;
        float scale = 50.0f + 175.0f * Mth.sin(oscillationRadians);

        poseStack.pushPose();
        switch (mode.getValue()) {
            case FadeOut -> {
                float x = (float) Math.sin(adjustedProgress * 112.0f / 180.0f) * 100.0f;
                float y = (float) Math.cos(adjustedProgress * 112.0f / 180.0f) * 50.0f;
                poseStack.translate(x * 0.008f, y * 0.008f, -10.0f);
                poseStack.scale(scale * 0.016f, scale * 0.016f, scale * 0.016f);
            }
            case Size -> {
                poseStack.translate(0.0f, 0.0f, -10.0f);
                poseStack.scale(scale * 0.016f, scale * 0.016f, scale * 0.016f);
            }
            case Otkisuli -> {
                poseStack.translate(0.0f, 0.0f, -10.0f);
                poseStack.mulPose(Axis.XP.rotationDegrees(adjustedProgress * 2.0f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(adjustedProgress * 2.0f));
                float size = 200.0f - adjustedProgress * 1.5f;
                poseStack.scale(size * 0.016f, size * 0.016f, size * 0.016f);
            }
            case Insert -> {
                poseStack.translate(0.0f, 0.0f, -10.0f);
                poseStack.mulPose(Axis.XP.rotationDegrees(adjustedProgress * 3.0f));
                float size = 200.0f - adjustedProgress * 1.5f;
                poseStack.scale(size * 0.016f, size * 0.016f, size * 0.016f);
            }
            case Fall -> {
                float downFactor = (float) Math.pow(adjustedProgress, 3) * 0.2f;
                poseStack.translate(0.0f, downFactor * 0.008f, -10.0f);
                poseStack.mulPose(Axis.ZP.rotationDegrees(adjustedProgress * 5.0f));
                float size = 200.0f - adjustedProgress * 1.5f;
                poseStack.scale(size * 0.016f, size * 0.016f, size * 0.016f);
            }
            case Rocket -> {
                float downFactor = (float) Math.pow(adjustedProgress, 3) * 0.2f - 20.0f;
                poseStack.translate(0.0f, -downFactor * 0.008f, -10.0f);
                poseStack.mulPose(Axis.YP.rotationDegrees(adjustedProgress * floatingItemTimeLeft * 2.0f));
                float size = 200.0f - adjustedProgress * 1.5f;
                poseStack.scale(size * 0.016f, size * 0.016f, size * 0.016f);
            }
            case Roll -> {
                float rightFactor = (float) Math.pow(adjustedProgress, 2) * 4.5f;
                poseStack.translate(rightFactor * 0.008f, 0.0f, -10.0f);
                poseStack.mulPose(Axis.ZP.rotationDegrees(adjustedProgress * 40.0f));
                float size = 200.0f - adjustedProgress * 1.5f;
                poseStack.scale(size * 0.016f, size * 0.016f, size * 0.016f);
            }
        }

        mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
        ItemStackRenderState itemState = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(itemState, floatingItem, ItemDisplayContext.FIXED, mc.level, null, 0);
        itemState.submit(poseStack, submitNodeCollector, 15728880, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    private int getTime() {
        int invertedSpeed = 101 - speed.getValue();
        return switch (mode.getValue()) {
            case FadeOut -> Math.max(1, invertedSpeed / 4);
            case Insert -> Math.max(1, invertedSpeed / 2);
            default -> Math.max(1, invertedSpeed);
        };
    }

}
