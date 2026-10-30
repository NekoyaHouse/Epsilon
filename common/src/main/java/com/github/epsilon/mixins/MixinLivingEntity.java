package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.modules.impl.player.InvManager;
import com.github.epsilon.modules.impl.player.JumpCooldown;
import com.github.epsilon.modules.impl.render.HandView;
import com.github.epsilon.modules.impl.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.github.epsilon.Constants.mc;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void preventSprintDuringInventorySorting(boolean sprinting, CallbackInfo ci) {
        InvManager invManager = InvManager.INSTANCE;
        if (sprinting && (LivingEntity) (Object) this == mc.player && invManager.isEnabled() && invManager.isSprintTransitionPending()) {
            ci.cancel();
        }
    }

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

    @ModifyReturnValue(method = "updateFallFlyingMovement", at = @At("RETURN"))
    private Vec3 modifyFallFlyingMovement(Vec3 original) {
        if ((Object) this != mc.player) {
            return original;
        }

        FallFlyingMovementEvent event = EventBus.INSTANCE.post(new FallFlyingMovementEvent(original));
        return event.getMovement();
    }

    @WrapOperation(method = "aiStep", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;noJumpDelay:I", opcode = Opcodes.PUTFIELD, ordinal = 1))
    private void redirectJumpingCooldown(LivingEntity instance, int value, Operation<Void> original) {
        JumpCooldown module = JumpCooldown.INSTANCE;
        int newValue = value;
        if (instance == mc.player && module.isEnabled()) {
            newValue = module.getCooldown();
        }
        original.call(instance, newValue);
    }

    @Inject(method = "getModifiedSwingDuration(Lnet/minecraft/world/item/component/SwingAnimation;)I", at = @At("HEAD"), cancellable = true)
    private void hookGetModifiedSwingDuration(SwingAnimation animation, CallbackInfoReturnable<Integer> cir) {
        HandView handView = HandView.INSTANCE;
        if ((LivingEntity) (Object) this == mc.player && handView.isEnabled() && handView.getModifySwingDuration()) {
            cir.setReturnValue(handView.getSwingDuration());
        }
    }

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("HEAD"), cancellable = true)
    private void onSwing(InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == mc.player) {
            SwingHandEvent event = EventBus.INSTANCE.post(new SwingHandEvent());
            if (event.isCancelled()) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "spawnItemParticles", at = @At("HEAD"), cancellable = true)
    private void onSpawnItemParticles(ItemStack itemStack, int count, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.getEatParticles()
                && itemStack.getComponents().has(net.minecraft.core.component.DataComponents.FOOD)) {
            ci.cancel();
        }
    }

}
