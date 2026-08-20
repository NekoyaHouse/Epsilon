package me.sofurry.via.injection.mixin.features.movement.liquid;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {

    public MixinLivingEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Shadow
    protected abstract float getWaterSlowDown();

    @Shadow
    public abstract boolean isJumping();

    @Redirect(method = "isInShallowFluid", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getFluidHeight(Lnet/minecraft/tags/TagKey;)D"))
    private double dontApplyLavaMovement(LivingEntity instance, TagKey<Fluid> tagKey) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2) && tagKey == FluidTags.LAVA) {
            return Double.MAX_VALUE;
        } else {
            return instance.getFluidHeight(tagKey);
        }
    }

    @Redirect(method = "travelInWater",
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/world/effect/MobEffects;DOLPHINS_GRACE:Lnet/minecraft/core/Holder;", opcode = Opcodes.GETSTATIC)),
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;horizontalCollision:Z", ordinal = 0, opcode = Opcodes.GETFIELD))
    private boolean disableClimbing(LivingEntity instance) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_13_2) && instance.horizontalCollision;
    }

    @ModifyVariable(method = "getFluidFallingAdjustedMovement", at = @At("HEAD"), argsOnly = true)
    private boolean modifyMovingDown(boolean isFalling) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_13_2) && isFalling;
    }

    @Redirect(method = "travelInWater", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSprinting()Z", ordinal = 0))
    private boolean modifySwimSprintSpeed(LivingEntity instance) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_12_2) && instance.isSprinting();
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getFluidHeight(Lnet/minecraft/tags/TagKey;)D"))
    private double redirectFluidHeight(LivingEntity instance, TagKey<Fluid> tagKey) {
        if ((ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)
                || ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest))
                && tagKey == FluidTags.WATER
                && this.isInWater()) {
            return 1;
        } else {
            return instance.getFluidHeight(tagKey);
        }
    }

    @Inject(method = "getFluidFallingAdjustedMovement", at = @At("HEAD"), cancellable = true)
    private void modifySwimSprintFallSpeed(double baseGravity, boolean isFalling, Vec3 movement, CallbackInfoReturnable<Vec3> ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) && !this.isNoGravity()) {
            ci.setReturnValue(new Vec3(movement.x, movement.y - 0.02, movement.z));
        }
    }

    @ModifyConstant(method = "travelInWater", constant = @Constant(floatValue = 0.9F))
    private float modifySwimFriction(float constant) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            return this.getWaterSlowDown();
        } else {
            return constant;
        }
    }

}
