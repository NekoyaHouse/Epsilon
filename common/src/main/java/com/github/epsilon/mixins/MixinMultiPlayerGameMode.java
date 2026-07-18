package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.AttackEntityEvent;
import com.github.epsilon.events.impl.DestroyBlockEvent;
import com.github.epsilon.events.impl.StartDestroyBlockEvent;
import com.github.epsilon.events.impl.UseItemEvent;
import com.github.epsilon.modules.impl.player.BreakCooldown;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {

    @ModifyArgs(method = "lambda$useItem$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ServerboundUseItemPacket;<init>(Lnet/minecraft/world/InteractionHand;IFF)V"))
    private void modifyUseItemPacketArgs(Args args) {
        UseItemEvent event = EventBus.INSTANCE.post(new UseItemEvent(args.get(2), args.get(3)));
        args.set(2, event.getYaw());
        args.set(3, event.getPitch());
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void onAttackEntity(Player player, Entity entity, CallbackInfo ci) {
        AttackEntityEvent event = EventBus.INSTANCE.post(new AttackEntityEvent(player, entity));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void onStartDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        StartDestroyBlockEvent event = EventBus.INSTANCE.post(new StartDestroyBlockEvent(pos, direction));
        if (event.isCancelled()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"), cancellable = true)
    public void hookDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        DestroyBlockEvent event = EventBus.INSTANCE.post(new DestroyBlockEvent(pos));
        if (event.isCancelled()) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(method = "continueDestroyBlock", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I", opcode = Opcodes.PUTFIELD, ordinal = 2))
    private void survivalBreakDelayChange(MultiPlayerGameMode instance, int value, Operation<Void> original) {
        BreakCooldown breakCooldown = BreakCooldown.INSTANCE;
        int newValue = breakCooldown.isEnabled() ? breakCooldown.cooldown.getValue() : value;
        original.call(instance, newValue);
    }

    @WrapOperation(method = "continueDestroyBlock", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I", opcode = Opcodes.PUTFIELD, ordinal = 1))
    private void creativeBreakDelayChangeOne(MultiPlayerGameMode instance, int value, Operation<Void> original) {
        BreakCooldown breakCooldown = BreakCooldown.INSTANCE;
        int newValue = breakCooldown.isEnabled() ? breakCooldown.cooldown.getValue() : value;
        original.call(instance, newValue);
    }

    @WrapOperation(method = "startDestroyBlock", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I", opcode = Opcodes.PUTFIELD))
    private void creativeBreakDelayChangeTwo(MultiPlayerGameMode instance, int value, Operation<Void> original) {
        BreakCooldown breakCooldown = BreakCooldown.INSTANCE;
        int newValue = breakCooldown.isEnabled() ? breakCooldown.cooldown.getValue() : value;
        original.call(instance, newValue);
    }

}
