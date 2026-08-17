package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.GameAnimation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class MixinScreen {

    @Unique
    private boolean epsilon$inventoryTransform;
    @Unique
    private float epsilon$inventoryScale = 1.0f;

    @Inject(method = "added", at = @At("HEAD"))
    private void beginInventoryAnimation(CallbackInfo ci) {
        if ((Screen) (Object) this instanceof AbstractContainerScreen<?>) {
            GameAnimation.INSTANCE.beginInventoryAnimation();
        }
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"))
    private void pushInventoryAnimation(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!((Screen) (Object) this instanceof AbstractContainerScreen<?>)) return;

        epsilon$inventoryScale = GameAnimation.INSTANCE.getInventoryScale();
        if (epsilon$inventoryScale >= 1.0f) return;

        epsilon$inventoryTransform = true;
        epsilon$scaleAroundCenter(graphics, epsilon$inventoryScale);
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
    private void popInventoryAnimation(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!epsilon$inventoryTransform) return;

        graphics.pose().popMatrix();
        epsilon$inventoryTransform = false;
    }

    @Inject(method = "extractTransparentBackground", at = @At("HEAD"))
    private void neutralizeBackgroundScale(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!epsilon$inventoryTransform) return;

        epsilon$scaleAroundCenter(graphics, 1.0f / epsilon$inventoryScale);
    }

    @Inject(method = "extractTransparentBackground", at = @At("RETURN"))
    private void restoreBackgroundScale(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (epsilon$inventoryTransform) graphics.pose().popMatrix();
    }

    @WrapOperation(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void transformBackgroundMouse(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, Operation<Void> original) {
        original.call(screen, graphics, epsilon$mouseX(graphics, mouseX), epsilon$mouseY(graphics, mouseY), delta);
    }

    @WrapOperation(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void transformContentMouse(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, Operation<Void> original) {
        original.call(screen, graphics, epsilon$mouseX(graphics, mouseX), epsilon$mouseY(graphics, mouseY), delta);
    }

    @WrapOperation(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V"))
    private void transformDeferredMouse(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, Operation<Void> original) {
        original.call(graphics, epsilon$mouseX(graphics, mouseX), epsilon$mouseY(graphics, mouseY), delta);
    }

    @Unique
    private void epsilon$scaleAroundCenter(GuiGraphicsExtractor graphics, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(graphics.guiWidth() / 2.0f, graphics.guiHeight() / 2.0f);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-graphics.guiWidth() / 2.0f, -graphics.guiHeight() / 2.0f);
    }

    @Unique
    private int epsilon$mouseX(GuiGraphicsExtractor graphics, int mouseX) {
        if (!epsilon$inventoryTransform) return mouseX;
        float centerX = graphics.guiWidth() / 2.0f;
        return Math.round(centerX + (mouseX - centerX) / epsilon$inventoryScale);
    }

    @Unique
    private int epsilon$mouseY(GuiGraphicsExtractor graphics, int mouseY) {
        if (!epsilon$inventoryTransform) return mouseY;
        float centerY = graphics.guiHeight() / 2.0f;
        return Math.round(centerY + (mouseY - centerY) / epsilon$inventoryScale);
    }

}
