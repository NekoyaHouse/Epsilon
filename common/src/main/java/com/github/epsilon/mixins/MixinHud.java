package com.github.epsilon.mixins;

import com.github.epsilon.elements.impl.island.Island;
import com.github.epsilon.modules.impl.render.BetterScoreboard;
import com.github.epsilon.modules.impl.render.FreeCamera;
import com.github.epsilon.modules.impl.render.GameAnimation;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"))
    private void onExtractScoreboardSidebar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
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

}
