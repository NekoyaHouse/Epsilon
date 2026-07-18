package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.FallFlyingEvent;
import com.github.epsilon.events.impl.JumpEvent;
import com.github.epsilon.events.impl.RotationAnimationEvent;
import com.github.epsilon.modules.impl.player.JumpCooldown;
import com.github.epsilon.modules.impl.render.HandsView;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.github.epsilon.Constants.mc;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {

    @WrapOperation(method = "tickHeadTurn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float modifyHeadYaw(LivingEntity entity, Operation<Float> original) {
        if (entity == mc.player) {
            RotationAnimationEvent event = EventBus.INSTANCE.post(new RotationAnimationEvent(entity.getYRot(), 0.0f, 0.0f, 0.0f));
            return event.getYaw();
        }
        return original.call(entity);
    }

    @ModifyExpressionValue(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float modifyJumpYaw(float original) {
        if ((Object) this == mc.player) {
            JumpEvent event = EventBus.INSTANCE.post(new JumpEvent(original));
            return event.getYaw();
        }
        return original;
    }

    @ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 modifyFallFlyingLookAngle(Vec3 original) {
        if ((Object) this == mc.player) {
            FallFlyingEvent event = EventBus.INSTANCE.post(new FallFlyingEvent(mc.player.getYRot(), mc.player.getXRot()));
            return mc.player.calculateViewVector(event.getPitch(), event.getYaw());
        }
        return original;
    }

    @ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
    private float modifyFallFlyingPitch(float original) {
        if ((Object) this == mc.player) {
            FallFlyingEvent event = EventBus.INSTANCE.post(new FallFlyingEvent(mc.player.getYRot(), original));
            return event.getPitch();
        }
        return original;
    }

    @WrapOperation(method = "aiStep", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;noJumpDelay:I", opcode = Opcodes.PUTFIELD, ordinal = 1))
    private void redirectJumpingCooldown(LivingEntity instance, int value, Operation<Void> original) {
        JumpCooldown module = JumpCooldown.INSTANCE;
        int newValue = value;
        if (instance == mc.player && module.isEnabled()) {
            newValue = module.cooldown.getValue();
        }
        original.call(instance, newValue);
    }

    @Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true)
    private void hookGetCurrentSwingDuration(CallbackInfoReturnable<Integer> cir) {
        HandsView handsView = HandsView.INSTANCE;
        if (handsView.isEnabled() && handsView.modifySwingDuration.getValue()) {
            cir.setReturnValue(handsView.swingDuration.getValue());
        }
    }

}
