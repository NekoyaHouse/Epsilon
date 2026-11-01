package com.github.epsilon.mixins;

import com.github.epsilon.events.impl.*;
import com.github.epsilon.modules.impl.player.BreakCooldown;
import com.github.epsilon.modules.impl.player.Disabler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {

    @Unique
    private float epsilon$oldYaw;

    @Unique
    private float epsilon$oldPitch;

    @Unique
    private boolean epsilon$rotationModified;

    @WrapMethod(method = "handleContainerInput")
    private void wrapInventoryClick(int containerId, int slotNum, int buttonNum, ContainerInput containerInput, Player player, Operation<Void> original) {
        Disabler.INSTANCE.handleInventoryClick(player, () -> original.call(containerId, slotNum, buttonNum, containerInput, player));
    }

    @Inject(method = "useItem", at = @At("HEAD"))
    private void preUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        UseItemEvent event = new UseItemEvent(player.getYRot(), player.getXRot()).post();
        if (event.isModified()) {
            epsilon$oldYaw = player.getYRot();
            epsilon$oldPitch = player.getXRot();
            player.setYRot(event.getYaw());
            player.setXRot(event.getPitch());
            epsilon$rotationModified = true;
        }
    }

    @Inject(method = "useItem", at = @At("RETURN"))
    private void postUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (epsilon$rotationModified) {
            player.setYRot(epsilon$oldYaw);
            player.setXRot(epsilon$oldPitch);
            epsilon$rotationModified = false;
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void onAttackEntity(Player player, Entity entity, CallbackInfo ci) {
        AttackEntityEvent event = new AttackEntityEvent(player, entity).post();
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void onStartDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        StartDestroyBlockEvent event = new StartDestroyBlockEvent(pos, direction).post();
        if (event.isCancelled()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        DestroyBlockEvent event = new DestroyBlockEvent(pos).post();
        if (event.isCancelled()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void onDestroyedBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        new DestroyedBlockEvent(pos).post();
    }

    @WrapOperation(method = "continueDestroyBlock", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I", opcode = Opcodes.PUTFIELD, ordinal = 2))
    private void survivalBreakDelayChange(MultiPlayerGameMode instance, int value, Operation<Void> original) {
        BreakCooldown breakCooldown = BreakCooldown.INSTANCE;
        int newValue = breakCooldown.isEnabled() ? breakCooldown.getCooldown() : value;
        original.call(instance, newValue);
    }

    @WrapOperation(method = "continueDestroyBlock", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I", opcode = Opcodes.PUTFIELD, ordinal = 1))
    private void creativeBreakDelayChangeOne(MultiPlayerGameMode instance, int value, Operation<Void> original) {
        BreakCooldown breakCooldown = BreakCooldown.INSTANCE;
        int newValue = breakCooldown.isEnabled() ? breakCooldown.getCooldown() : value;
        original.call(instance, newValue);
    }

    @WrapOperation(method = "startDestroyBlock", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I", opcode = Opcodes.PUTFIELD))
    private void creativeBreakDelayChangeTwo(MultiPlayerGameMode instance, int value, Operation<Void> original) {
        BreakCooldown breakCooldown = BreakCooldown.INSTANCE;
        int newValue = breakCooldown.isEnabled() ? breakCooldown.getCooldown() : value;
        original.call(instance, newValue);
    }

}
