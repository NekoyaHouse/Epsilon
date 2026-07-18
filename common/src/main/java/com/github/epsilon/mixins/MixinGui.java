package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.FreeCamera;
import com.github.epsilon.modules.impl.render.GameAnimation;
import com.github.epsilon.modules.impl.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinGui {

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void onExtractEffects(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        NoRender noRender = NoRender.INSTANCE;
        if (noRender.isEnabled() && noRender.potionEffects.getValue()) {
            ci.cancel();
        }
    }

    @ModifyArg(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 1), index = 2)
    private int modifyHotbarSelectionX(int x) {
        return GameAnimation.INSTANCE.getHotbarSelectionX(x);
    }

    @ModifyExpressionValue(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    private boolean alwaysRenderCrosshairInFreecam(boolean firstPerson) {
        return FreeCamera.INSTANCE.isEnabled() || firstPerson;
    }

}
