package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.GameAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(InventoryScreen.class)
public class MixinInventoryScreen {

    @ModifyArgs(method = {"extractEntityInInventoryFollowsMouse", "renderEntityInInventoryFollowsAngle"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;entity(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FLorg/joml/Vector3fc;Lorg/joml/Quaternionfc;Lorg/joml/Quaternionfc;IIII)V"))
    private static void animateInventoryEntity(Args args) {
        float animationScale = GameAnimation.INSTANCE.getCurrentInventoryScale();
        if (animationScale >= 1.0f) return;

        float centerX = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2.0f;
        float centerY = Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2.0f;

        args.set(1, (Float) args.get(1) * animationScale);
        args.set(5, epsilon$scaleCoordinate(args.get(5), centerX, animationScale));
        args.set(6, epsilon$scaleCoordinate(args.get(6), centerY, animationScale));
        args.set(7, epsilon$scaleCoordinate(args.get(7), centerX, animationScale));
        args.set(8, epsilon$scaleCoordinate(args.get(8), centerY, animationScale));
    }

    @Unique
    private static int epsilon$scaleCoordinate(int coordinate, float center, float scale) {
        return Math.round(center + (coordinate - center) * scale);
    }

}
