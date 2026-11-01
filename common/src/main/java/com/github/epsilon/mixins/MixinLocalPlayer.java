package com.github.epsilon.mixins;

import com.github.epsilon.events.impl.*;
import com.github.epsilon.modules.impl.movement.Velocity;
import com.github.epsilon.modules.impl.player.InvManager;
import com.github.epsilon.modules.impl.render.TotemAnimation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer extends AbstractClientPlayer {

    @Shadow
    protected int sprintTriggerTime;

    @Shadow
    private double xLast;

    @Shadow
    private double yLast;

    @Shadow
    private double zLast;

    @Shadow
    private float yRotLast;

    @Shadow
    private float xRotLast;

    @Shadow
    private int positionReminder;

    @Unique
    private SendPositionEvent epsilon$sendPositionEvent;

    protected MixinLocalPlayer(ClientLevel level, GameProfile gameProfile) {
        super(level, gameProfile);
    }

    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void preventSprintDuringInventorySorting(CallbackInfoReturnable<Boolean> cir) {
        InvManager invManager = InvManager.INSTANCE;
        if (invManager.isEnabled() && invManager.isSprintTransitionPending()) {
            this.sprintTriggerTime = 0;
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V", shift = At.Shift.BEFORE, ordinal = 0), cancellable = true)
    private void onPreTick(CallbackInfo ci) {
        PlayerTickEvent.Pre event = new PlayerTickEvent.Pre().post();
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V", shift = At.Shift.AFTER, ordinal = 0))
    private void onPostTick(CallbackInfo ci) {
        new PlayerTickEvent.Post().post();
    }

    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void onPreSendPosition(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        epsilon$sendPositionEvent = new SendPositionEvent(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), player.onGround(), new Vec3(xLast, yLast, zLast), yRotLast, xRotLast, positionReminder).post();
        if (epsilon$sendPositionEvent.isCancelled()) {
            ci.cancel();
            new AfterSendPositionEvent().post();
        }
    }

    @ModifyVariable(method = "sendPosition", at = @At("STORE"), name = "move")
    private boolean forcePositionPacket(boolean move) {
        return move || epsilon$sendPositionEvent.getForcePosition();
    }

    @ModifyVariable(method = "sendPosition", at = @At("STORE"), name = "rot")
    private boolean forceRotationPacket(boolean rot) {
        return rot || epsilon$sendPositionEvent.getForceRotation();
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void onPostSendPosition(CallbackInfo ci) {
        new AfterSendPositionEvent().post();
    }

    @Inject(method = "displayItemActivation(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void onDisplayItemActivation(ItemStack itemStack, CallbackInfo ci) {
        if (TotemAnimation.INSTANCE.isEnabled()) {
            TotemAnimation.INSTANCE.showFloatingItem(itemStack);
            ci.cancel();
        }
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectPosition(LocalPlayer instance, Operation<Vec3> original) {
        return new Vec3(epsilon$sendPositionEvent.getX(), epsilon$sendPositionEvent.getY(), epsilon$sendPositionEvent.getZ());
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"))
    private double redirectGetX(LocalPlayer instance, Operation<Double> original) {
        return epsilon$sendPositionEvent.getX();
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"))
    private double redirectGetY(LocalPlayer instance, Operation<Double> original) {
        return epsilon$sendPositionEvent.getY();
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"))
    private double redirectGetZ(LocalPlayer instance, Operation<Double> original) {
        return epsilon$sendPositionEvent.getZ();
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float redirectGetYRot(LocalPlayer instance, Operation<Float> original) {
        return epsilon$sendPositionEvent.getYaw();
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float redirectGetXRot(LocalPlayer instance, Operation<Float> original) {
        return epsilon$sendPositionEvent.getPitch();
    }

    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;onGround()Z"))
    private boolean redirectOnGround(LocalPlayer instance, Operation<Boolean> original) {
        return epsilon$sendPositionEvent.isOnGround();
    }

    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void hookPushOutOfBlocks(double x, double d, CallbackInfo info) {
        if (Velocity.INSTANCE.isEnabled() && Velocity.INSTANCE.getMode() == Velocity.Mode.Cancel && Velocity.INSTANCE.getBlockPush()) {
            info.cancel();
        }
    }

    @WrapOperation(method = "modifyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean onSlowdown(LocalPlayer localPlayer, Operation<Boolean> original) {
        SlowdownEvent event = new SlowdownEvent(original.call(localPlayer)).post();
        return event.isSlowdown();
    }

    @Inject(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"), cancellable = true)
    private void onMove(MoverType moverType, Vec3 delta, CallbackInfo ci) {
        MoveEvent event = new MoveEvent(delta.x, delta.y, delta.z).post();
        if (event.isCancelled()) {
            super.move(moverType, new Vec3(event.getX(), event.getY(), event.getZ()));
            ci.cancel();
        }
    }

}
