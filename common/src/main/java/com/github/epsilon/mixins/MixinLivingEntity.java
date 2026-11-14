package com.github.epsilon.mixins;

import com.github.epsilon.events.impl.*;
import com.github.epsilon.modules.impl.player.InvManager;
import com.github.epsilon.modules.impl.player.JumpCooldown;
import com.github.epsilon.modules.impl.render.HandView;
import com.github.epsilon.modules.impl.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
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

import static com.github.epsilon.kernel.MinecraftWrapper.mc;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void preventSprintDuringInventorySorting(boolean sprinting, CallbackInfo ci) {
        InvManager invManager = InvManager.INSTANCE;
        if (sprinting && (Object) this == mc.player && invManager.isEnabled() && invManager.isSprintTransitionPending()) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "tickHeadTurn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float modifyHeadYaw(LivingEntity entity, Operation<Float> original) {
        if (entity == mc.player) {
            RotationAnimationEvent event = new RotationAnimationEvent(entity.getYRot(), 0.0f, 0.0f, 0.0f);
            event.post();
            return event.getYaw();
        }
        return original.call(entity);
    }

    @ModifyExpressionValue(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float modifyJumpYaw(float original) {
        if ((Object) this == mc.player) {
            JumpEvent event = new JumpEvent(original);
            event.post();
            return event.getYaw();
        }
        return original;
    }

    @ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 modifyFallFlyingLookAngle(Vec3 original) {
        if ((Object) this == mc.player) {
            FallFlyingEvent event = new FallFlyingEvent(mc.player.getYRot(), mc.player.getXRot());
            event.post();
            return Entity.calculateViewVector(event.getPitch(), event.getYaw());
        }
        return original;
    }

    @ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
    private float modifyFallFlyingPitch(float original) {
        if ((Object) this == mc.player) {
            FallFlyingEvent event = new FallFlyingEvent(mc.player.getYRot(), original);
            event.post();
            return event.getPitch();
        }
        return original;
    }

    @ModifyReturnValue(method = "updateFallFlyingMovement", at = @At("RETURN"))
    private Vec3 modifyFallFlyingMovement(Vec3 original) {
        if ((Object) this != mc.player) {
            return original;
        }

        FallFlyingMovementEvent event = new FallFlyingMovementEvent(original);
        event.post();
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
        if ((Object) this == mc.player && handView.isEnabled() && handView.getModifySwingDuration()) {
            cir.setReturnValue(handView.getSwingDuration());
        }
    }

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("HEAD"), cancellable = true)
    private void onSwing(InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == mc.player) {
            SwingHandEvent event = new SwingHandEvent();
            event.post();
            if (event.isCancelled()) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "spawnItemParticles", at = @At("HEAD"), cancellable = true)
    private void epsilon$hideEatingParticles(ItemStack stack, int count, CallbackInfo ci) {
        if (NoRender.INSTANCE.noEatParticles() && stack.has(DataComponents.FOOD)) ci.cancel();
    }

}
