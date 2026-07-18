package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.AttackSlowDownEvent;
import com.github.epsilon.events.impl.AttackYawEvent;
import com.github.epsilon.events.impl.TravelEvent;
import com.github.epsilon.modules.impl.movement.KeepSprint;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.github.epsilon.Constants.mc;

@Mixin(Player.class)
public class MixinPlayer {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onTravelPre(Vec3 input, CallbackInfo ci) {
        if ((Player) (Object) this == mc.player) {
            TravelEvent event = EventBus.INSTANCE.post(new TravelEvent());
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @ModifyExpressionValue(method = {"causeExtraKnockback", "doSweepAttack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
    private float modifyAttackYaw(float original) {
        AttackYawEvent event = EventBus.INSTANCE.post(new AttackYawEvent(original));
        return event.getYaw();
    }

    @Inject(method = "causeExtraKnockback", at = @At("HEAD"), cancellable = true)
    private void onCauseExtraKnockback(Entity entity, float knockbackAmount, Vec3 oldMovement, CallbackInfo ci) {
        AttackSlowDownEvent event = EventBus.INSTANCE.post(new AttackSlowDownEvent(entity, knockbackAmount));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void onAfterAttack(Entity entity, CallbackInfo ci) {
        KeepSprint keepSprint = KeepSprint.INSTANCE;
        if ((Player) (Object) this == mc.player && keepSprint.isEnabled() && keepSprint.shouldKeepSprint()) {
            // Re-enable sprint if vanilla attack stopped it
            if (!mc.player.isSprinting()) {
                mc.player.setSprinting(true);
            }
            // Apply custom slowdown factor (matching LeaderClient formula)
            double slowdownPercent = keepSprint.slowdown.getValue().doubleValue() / 100.0;
            if (slowdownPercent > 0.0) {
                double customFactor = 0.6 + 0.4 * (1.0 - slowdownPercent);
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(customFactor, 1.0, customFactor));
            }
        }
    }

}
