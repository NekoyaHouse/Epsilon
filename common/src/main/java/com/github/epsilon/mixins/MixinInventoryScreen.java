package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.GameAnimation;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import static com.github.epsilon.kernel.MinecraftWrapper.mc;

@Mixin(InventoryScreen.class)
public class MixinInventoryScreen {

    @ModifyArgs(method = "extractEntityInInventoryFollowsMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;entity(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FLorg/joml/Vector3fc;Lorg/joml/Quaternionfc;Lorg/joml/Quaternionfc;IIII)V"), require = 0)
    private static void animateInventoryEntity(Args args) {
        epsilon$scaleInventoryEntity(args);
    }

    @ModifyArgs(method = "renderEntityInInventoryFollowsAngle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;entity(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FLorg/joml/Vector3fc;Lorg/joml/Quaternionfc;Lorg/joml/Quaternionfc;IIII)V"), require = 0)
    private static void animateInventoryEntityNeoForge(Args args) {
        epsilon$scaleInventoryEntity(args);
    }

    @Unique
    private static void epsilon$scaleInventoryEntity(Args args) {
        float animationScale = GameAnimation.INSTANCE.getCurrentInventoryScale();
        if (animationScale >= 1.0f) return;

        float centerX = mc.getWindow().getGuiScaledWidth() / 2.0f;
        float centerY = mc.getWindow().getGuiScaledHeight() / 2.0f;

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
