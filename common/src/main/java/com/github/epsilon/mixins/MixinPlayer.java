package com.github.epsilon.mixins;

import com.github.epsilon.events.impl.AttackSlowdownEvent;
import com.github.epsilon.events.impl.AttackYawEvent;
import com.github.epsilon.events.impl.TravelEvent;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.github.epsilon.kernel.MinecraftWrapper.mc;

@Mixin(Player.class)
public class MixinPlayer {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onTravelPre(Vec3 input, CallbackInfo ci) {
        if ((Object) this == mc.player) {
            TravelEvent event = new TravelEvent().post();
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @ModifyExpressionValue(method = {"causeExtraKnockback", "doSweepAttack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
    private float modifyAttackYaw(float original) {
        AttackYawEvent event = new AttackYawEvent(original).post();
        return event.getYaw();
    }

    @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private boolean onAttackSlowdown(Player instance, Vec3 movement, @Share("cancelAttackSlowdown") LocalBooleanRef cancelled) {
        AttackSlowdownEvent event = new AttackSlowdownEvent().post();
        cancelled.set(event.isCancelled());
        return !cancelled.get();
    }

    @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V"))
    private boolean onAttackStopSprinting(Player instance, boolean sprinting, @Share("cancelAttackSlowdown") LocalBooleanRef cancelled) {
        return !cancelled.get();
    }

}
