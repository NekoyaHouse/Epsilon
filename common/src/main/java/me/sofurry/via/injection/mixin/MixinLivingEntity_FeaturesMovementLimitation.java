package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_FeaturesMovementLimitation extends Entity {

    public MixinLivingEntity_FeaturesMovementLimitation(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Shadow
    public abstract float getSpeed();

    @Shadow
    protected abstract float getFlyingSpeed();

    @Shadow
    public abstract boolean hasEffect(Holder<MobEffect> effect);

    @ModifyConstant(method = "getFrictionInfluencedSpeed", constant = @Constant(doubleValue = 0.6))
    private double allowFrictionLessThan(double constant) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            return Double.MIN_VALUE;
        } else {
            return constant;
        }
    }

    @Redirect(method = "computeModifiedFriction", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F"))
    private static float dontClampFriction(float value, float min, float max) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            return value;
        } else {
            return Mth.clamp(value, min, max);
        }
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;is(Ljava/lang/Object;)Z"))
    private boolean useEuclideanDistanceCalculation(LivingEntity instance, Object o) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            return false;
        } else {
            return instance.is((EntityType<?>) o);
        }
    }

    @Inject(method = "getFrictionInfluencedSpeed", at = @At("HEAD"), cancellable = true)
    private void modifyFrictionInfluencedSpeed(float blockFriction, CallbackInfoReturnable<Float> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            float drag = this.onGround() ? blockFriction * 0.91F : 0.91F;
            float accel = 0.16277136F / (drag * drag * drag);
            cir.setReturnValue(this.onGround() ? this.getSpeed() * accel : this.getFlyingSpeed());
        }
    }

    @Redirect(method = "travelInAir", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;hasChunkAt(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean modifyLoadedCheck(Level instance, BlockPos blockPos) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            return instance.hasChunkAt(blockPos) && instance.getChunkSource().hasChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
        } else {
            return instance.hasChunkAt(blockPos);
        }
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;resetFallDistance()V"))
    private void dontResetLevitationFallDistance(LivingEntity instance) {
        // Only needed when mods calculate the fall distance on the clientside
        if (this.hasEffect(MobEffects.SLOW_FALLING) || ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_12_2)) {
            instance.resetFallDistance();
        }
    }

}
