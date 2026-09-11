package com.github.epsilon.mixins;

import com.github.epsilon.elements.impl.island.Island;
import com.github.epsilon.modules.impl.render.BetterScoreboard;
import com.github.epsilon.modules.impl.render.FreeCamera;
import com.github.epsilon.modules.impl.render.GameAnimation;
import com.github.epsilon.modules.impl.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class MixinHud {

    @ModifyArg(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 1), index = 2)
    private int modifyHotbarSelectionX(int x) {
        return GameAnimation.INSTANCE.getHotbarSelectionX(x);
    }

    @ModifyExpressionValue(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    private boolean alwaysRenderCrosshairInFreecam(boolean firstPerson) {
        return FreeCamera.INSTANCE.isEnabled() || firstPerson;
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void onExtractEffects(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.potionIcons.getValue()) ci.cancel();
    }

    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void onExtractPortalOverlay(GuiGraphicsExtractor graphics, float alpha, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.portalOverlay.getValue()) ci.cancel();
    }

    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void onExtractVignette(GuiGraphicsExtractor graphics, Entity camera, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.vignette.getValue()) ci.cancel();
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void onExtractScoreboardSidebar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (BetterScoreboard.INSTANCE.isEnabled()) BetterScoreboard.INSTANCE.beginScoreboardExtraction();
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.scoreboard.getValue()) ci.cancel();
    }

    @Inject(method = "extractChat", at = @At("HEAD"), cancellable = true)
    private void onExtractChat(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.chat.getValue()) ci.cancel();
    }

    @WrapOperation(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void replaceScoreboardBackground(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int col, Operation<Void> original) {
        if (BetterScoreboard.INSTANCE.isEnabled()) {
            BetterScoreboard.INSTANCE.captureVanillaBackground(x0, y0, x1, y1);
        } else {
            original.call(graphics, x0, y0, x1, y1, col);
        }
    }

    @WrapOperation(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"))
    private void offsetScoreboardText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, boolean dropShadow, Operation<Void> original) {
        if (BetterScoreboard.INSTANCE.isEnabled()) {
            x += BetterScoreboard.INSTANCE.getRoundedXOffset();
            y += BetterScoreboard.INSTANCE.getRoundedYOffset();
        }
        original.call(graphics, font, text, x, y, color, dropShadow);
    }

    @Inject(method = "extractSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void onExtractSpyglassOverlay(GuiGraphicsExtractor graphics, float scale, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.spyglassOverlay.getValue()) ci.cancel();
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void onExtractCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.crosshair.getValue()) ci.cancel();
    }

    @Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
    private void onExtractTitle(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.title.getValue()) ci.cancel();
    }

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void onExtractSelectedItemName(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.heldItemName.getValue()) ci.cancel();
    }

    @Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void onExtractConfusionOverlay(GuiGraphicsExtractor graphics, float strength, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.nausea.getValue()) ci.cancel();
    }

    @Inject(method = "extractTabList", at = @At("HEAD"), cancellable = true)
    private void onExtractTabList(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Island.INSTANCE.isEnabled()) ci.cancel();
    }

}
