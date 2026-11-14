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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Hud.class)
public class MixinHud {

    @ModifyArg(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 1), index = 2)
    private int modifyHotbarSelectionX(int x) {
        return GameAnimation.INSTANCE.getHotbarSelectionX(x);
    }

    @ModifyExpressionValue(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    private boolean alwaysRenderCrosshairInFreecam(boolean firstPerson) {
        return FreeCamera.INSTANCE.isEnabled() || firstPerson;
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void onExtractScoreboardSidebar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRender.INSTANCE.noScoreboard()) {
            ci.cancel();
            return;
        }
        if (BetterScoreboard.INSTANCE.isEnabled()) BetterScoreboard.INSTANCE.beginScoreboardExtraction();
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

    @Inject(method = "extractTabList", at = @At("HEAD"), cancellable = true)
    private void onExtractTabList(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Island.INSTANCE.isEnabled()) ci.cancel();
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void epsilon$extractEffects(CallbackInfo ci) {
        if (NoRender.INSTANCE.noPotionIcons()) ci.cancel();
    }

    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void epsilon$extractPortalOverlay(CallbackInfo ci) {
        if (NoRender.INSTANCE.noPortalOverlay()) ci.cancel();
    }

    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void epsilon$extractVignette(CallbackInfo ci) {
        if (NoRender.INSTANCE.noVignette()) ci.cancel();
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void epsilon$displayScoreboardSidebar(CallbackInfo ci) {
        if (NoRender.INSTANCE.noScoreboard()) ci.cancel();
    }

    @Inject(method = "extractSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void epsilon$extractSpyglassOverlay(CallbackInfo ci) {
        if (NoRender.INSTANCE.noSpyglassOverlay()) ci.cancel();
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void epsilon$extractCrosshair(CallbackInfo ci) {
        if (NoRender.INSTANCE.noCrosshair()) ci.cancel();
    }

    @Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
    private void epsilon$extractTitle(CallbackInfo ci) {
        if (NoRender.INSTANCE.noTitle()) ci.cancel();
    }

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void epsilon$extractSelectedItemName(CallbackInfo ci) {
        if (NoRender.INSTANCE.noHeldItemName()) ci.cancel();
    }

    @Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void epsilon$extractConfusionOverlay(CallbackInfo ci) {
        if (NoRender.INSTANCE.noNausea()) ci.cancel();
    }

    @Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true)
    private void epsilon$extractBossOverlay(CallbackInfo ci) {
        if (NoRender.INSTANCE.noBossBar()) ci.cancel();
    }

    @ModifyArgs(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V", ordinal = 0))
    private void epsilon$hidePumpkinOverlay(Args args) {
        if (NoRender.INSTANCE.noPumpkinOverlay()) args.set(2, 0.0F);
    }

    @ModifyArgs(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V", ordinal = 1))
    private void epsilon$hidePowderSnowOverlay(Args args) {
        if (NoRender.INSTANCE.noPowderedSnowOverlay()) args.set(2, 0.0F);
    }

}
