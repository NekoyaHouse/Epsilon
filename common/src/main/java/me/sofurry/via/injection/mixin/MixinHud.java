package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Hud.class)
public abstract class MixinHud {

    @Unique
    private static final int viaVersionVisuals$ARMOR_ICON_WIDTH = 8;

    @Inject(method = "playAirBubblePoppedSound", at = @At("HEAD"), cancellable = true)
    private void disableBubblePopSound(int bubble, Player player, int emptyAirBubbles, CallbackInfo ci) {
        if (ClientSetting.INSTANCE.removeBubblePopSound.getValue()) {
            ci.cancel();
        }
    }

    @WrapWithCondition(method = "extractAirBubbles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 2))
    private boolean disableEmptyBubbles(GuiGraphicsExtractor instance, RenderPipeline renderPipeline, Identifier location, int x, int y, int width, int height) {
        return !ClientSetting.INSTANCE.hideEmptyBubbleIcons.getValue();
    }

    @WrapOperation(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    private boolean alwaysRenderCrosshair(CameraType instance, Operation<Boolean> original) {
        if (ClientSetting.INSTANCE.alwaysRenderCrosshair.isEnabled()) {
            return true;
        } else {
            return original.call(instance);
        }
    }

    @Inject(method = "willPrioritizeJumpInfo", at = @At("HEAD"), cancellable = true)
    private void removeMountJumpBar(CallbackInfoReturnable<Boolean> cir) {
        if (ClientSetting.INSTANCE.hideModernHUDElements.isEnabled()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "extractVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void removeMountJumpBar(CallbackInfo ci) {
        if (ClientSetting.INSTANCE.hideModernHUDElements.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "getVehicleMaxHearts", at = @At("HEAD"), cancellable = true)
    private void removeHungerBar(LivingEntity vehicle, CallbackInfoReturnable<Integer> cir) {
        if (ClientSetting.INSTANCE.hideModernHUDElements.isEnabled()) {
            cir.setReturnValue(1);
        }
    }

    @ModifyExpressionValue(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;guiHeight()I"), require = 0)
    private int moveHealthDown(int value) {
        if (ClientSetting.INSTANCE.hideModernHUDElements.isEnabled()) {
            return value + 7; // Magical offset
        } else {
            return value;
        }
    }

    @ModifyArgs(method = "extractArmor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"), require = 0)
    private static void moveArmorPositions(Args args) {
        if (ClientSetting.INSTANCE.hideModernHUDElements.isEnabled()) {
            final int width = 10 * viaVersionVisuals$ARMOR_ICON_WIDTH;
            args.set(2, (int) args.get(2) + width + 21);
            args.set(3, (int) args.get(3) + 10);
        }
    }

    @ModifyArg(method = "extractAirBubbles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"), index = 2, require = 0)
    private int moveAirBubbles(int value) {
        if (ClientSetting.INSTANCE.hideModernHUDElements.isEnabled()) {
            final Minecraft client = Minecraft.getInstance();
            return client.getWindow().getGuiScaledWidth() - value - client.font.lineHeight;
        } else {
            return value;
        }
    }

}
