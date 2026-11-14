package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.CameraClip;
import com.github.epsilon.modules.impl.render.FreeCamera;
import com.github.epsilon.modules.impl.render.NoRender;
import com.github.epsilon.modules.impl.render.SneakTweak;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import static com.github.epsilon.kernel.MinecraftWrapper.mc;

@Mixin(Camera.class)
public class MixinCamera {

    @Shadow
    private Entity entity;

    @Shadow
    private boolean detached;

    @Shadow
    private float eyeHeight;

    @Shadow
    private float eyeHeightOld;

    @Unique
    private Pose[] epsilon$lastPoses;

    @Unique
    private Pose[] epsilon$getLastPoses() {
        if (epsilon$lastPoses == null) {
            epsilon$lastPoses = new Pose[2];
        }
        return epsilon$lastPoses;
    }

    @Unique
    private boolean epsilon$isStandingCrouchingTransition() {
        Pose[] lastPoses = epsilon$getLastPoses();
        return lastPoses[1] == Pose.STANDING && lastPoses[0] == Pose.CROUCHING || lastPoses[1] == Pose.CROUCHING && lastPoses[0] == Pose.STANDING;
    }

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void hookGetMaxZoom(float cameraDist, CallbackInfoReturnable<Float> cir) {
        if (FreeCamera.INSTANCE.isEnabled()) {
            cir.setReturnValue(0.0f);
        } else {
            CameraClip cameraClip = CameraClip.INSTANCE;
            if (cameraClip.isEnabled()) {
                cir.setReturnValue((float) cameraClip.getDistance());
            }
        }
    }

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void onAlignWithEntityTail(float partialTicks, CallbackInfo ci) {
        if (FreeCamera.INSTANCE.isEnabled()) {
            this.detached = true;
        }
    }

    @ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void onAlignSetPosArgs(Args args, @Local(argsOnly = true) float partialTicks) {
        FreeCamera freeCamera = FreeCamera.INSTANCE;
        if (freeCamera.isEnabled()) {
            args.set(0, freeCamera.getX(partialTicks));
            args.set(1, freeCamera.getY(partialTicks));
            args.set(2, freeCamera.getZ(partialTicks));
        } else {
            CameraClip cameraClip = CameraClip.INSTANCE;
            if (cameraClip.isEnabled() && cameraClip.getAction()) {
                if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
                    Vec3 targetPos = new Vec3(args.get(0), args.get(1), args.get(2));
                    cameraClip.updateActionCamera(targetPos);
                    Vec3 cameraPos = cameraClip.getCameraPos();
                    if (cameraPos != null) {
                        args.set(0, cameraPos.x);
                        args.set(1, cameraPos.y);
                        args.set(2, cameraPos.z);
                    }
                } else {
                    cameraClip.resetCameraPos();
                }
            }
        }
    }

    @ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"), require = 0)
    private void onAlignSetRotation(Args args, @Local(argsOnly = true) float partialTicks) {
        FreeCamera freeCamera = FreeCamera.INSTANCE;
        if (freeCamera.isEnabled()) {
            args.set(0, (float) freeCamera.getYaw(partialTicks));
            args.set(1, (float) freeCamera.getPitch(partialTicks));
        }
    }

    @ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FFF)V"), require = 0)
    private void onAlignSetRotationNeoForge(Args args, @Local(argsOnly = true) float partialTicks) {
        FreeCamera freeCamera = FreeCamera.INSTANCE;
        if (freeCamera.isEnabled()) {
            args.set(0, (float) freeCamera.getYaw(partialTicks));
            args.set(1, (float) freeCamera.getPitch(partialTicks));
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void hookSneakTweakCameraTick(CallbackInfo ci) {
        if (entity == null) {
            return;
        }

        Pose[] lastPoses = epsilon$getLastPoses();
        Pose pose = entity.getPose();
        if (pose != lastPoses[0]) {
            lastPoses[1] = lastPoses[0];
            lastPoses[0] = pose;
        }

        if (SneakTweak.INSTANCE.shouldSnapCameraEyeHeight() && epsilon$isStandingCrouchingTransition()) {
            eyeHeight = entity.getEyeHeight();
            eyeHeightOld = eyeHeight;
        }
    }

    @ModifyConstant(method = "tick", constant = @Constant(floatValue = 0.5F))
    private float hookSneakTweakCameraSpeed(float modifier) {
        if (epsilon$isStandingCrouchingTransition()) {
            return SneakTweak.INSTANCE.getCameraSmoothingModifier(modifier);
        }
        return modifier;
    }

    @Inject(method = "getFluidInCamera", at = @At("HEAD"), cancellable = true)
    private void epsilon$hideLiquidOverlay(CallbackInfoReturnable<FogType> cir) {
        if (NoRender.INSTANCE.noLiquidOverlay()) cir.setReturnValue(FogType.NONE);
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean epsilon$showSky(LivingEntity entity, Holder<MobEffect> effect, Operation<Boolean> original) {
        if (effect == MobEffects.BLINDNESS && NoRender.INSTANCE.noBlindness()) return false;
        if (effect == MobEffects.DARKNESS && NoRender.INSTANCE.noDarkness()) return false;
        return original.call(entity, effect);
    }

}
