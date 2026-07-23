package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.FreeCamera;
import com.github.epsilon.modules.impl.render.GameAnimation;
import com.github.epsilon.modules.impl.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Gui.class)
public class MixinGui {

    @ModifyArg(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 1), index = 2)
    private int modifyHotbarSelectionX(int x) {
        return GameAnimation.INSTANCE.getHotbarSelectionX(x);
    }

    @ModifyExpressionValue(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    private boolean alwaysRenderCrosshairInFreecam(boolean firstPerson) {
        return FreeCamera.INSTANCE.isEnabled() || firstPerson;
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void onExtractEffects(CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.potionIcons.getValue()) ci.cancel();
    }

    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void onExtractPortalOverlay(GuiGraphicsExtractor graphics, float alpha, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.portalOverlay.getValue()) ci.cancel();
    }

    @ModifyArgs(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V", ordinal = 0))
    private void onExtractPumpkinOverlay(Args args) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.pumpkinOverlay.getValue()) args.set(2, 0f);
    }

    @ModifyArgs(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V", ordinal = 1))
    private void onExtractPowderedSnowOverlay(Args args) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.powderedSnowOverlay.getValue()) args.set(2, 0f);
    }

    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void onExtractVignette(GuiGraphicsExtractor graphics, Entity camera, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.vignette.getValue()) ci.cancel();
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void onDisplayScoreboardSidebar(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.scoreboard.getValue()) ci.cancel();
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void onExtractScoreboardSidebar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.scoreboard.getValue()) ci.cancel();
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

}
