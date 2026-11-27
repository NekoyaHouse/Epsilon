package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.GameAnimation;
import com.github.epsilon.modules.impl.render.NoRender;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
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

    @WrapMethod(method = "extractRenderStateWithTooltipAndSubtitles")
    private void extractWithInventoryAnimation(GuiGraphicsExtractor graphics, Operation<Void> original) {
        if (!((Screen) (Object) this instanceof AbstractContainerScreen<?>)) {
            original.call(graphics);
            return;
        }

        epsilon$inventoryScale = GameAnimation.INSTANCE.getInventoryScale();
        if (epsilon$inventoryScale >= 1.0f) {
            original.call(graphics);
            return;
        }

        int mouseX = graphics.mouseX;
        int mouseY = graphics.mouseY;
        epsilon$inventoryTransform = true;
        epsilon$scaleAroundCenter(graphics, epsilon$inventoryScale);
        // Since 26.4 both screen rendering and deferred tooltips read the cursor from the extractor.
        graphics.mouseX = epsilon$transformMouse(mouseX, graphics.guiWidth());
        graphics.mouseY = epsilon$transformMouse(mouseY, graphics.guiHeight());
        try {
            original.call(graphics);
        } finally {
            graphics.mouseX = mouseX;
            graphics.mouseY = mouseY;
            graphics.pose().popMatrix();
            epsilon$inventoryTransform = false;
        }
    }

    @Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
    private void neutralizeBackgroundScale(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (Minecraft.getInstance().level != null && NoRender.INSTANCE.noGuiBackground()) {
            ci.cancel();
            return;
        }
        if (!epsilon$inventoryTransform) return;

        epsilon$scaleAroundCenter(graphics, 1.0f / epsilon$inventoryScale);
    }

    @Inject(method = "extractTransparentBackground", at = @At("RETURN"))
    private void restoreBackgroundScale(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (epsilon$inventoryTransform) graphics.pose().popMatrix();
    }

    @Unique
    private void epsilon$scaleAroundCenter(GuiGraphicsExtractor graphics, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(graphics.guiWidth() / 2.0f, graphics.guiHeight() / 2.0f);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-graphics.guiWidth() / 2.0f, -graphics.guiHeight() / 2.0f);
    }

    @Unique
    private int epsilon$transformMouse(int coordinate, int size) {
        float center = size / 2.0f;
        return Math.round(center + (coordinate - center) / epsilon$inventoryScale);
    }

}
