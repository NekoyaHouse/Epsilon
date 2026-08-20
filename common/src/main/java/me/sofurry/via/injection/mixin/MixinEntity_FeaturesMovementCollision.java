package me.sofurry.via.injection.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Entity.class)
public abstract class MixinEntity_FeaturesMovementCollision {

    @Shadow
    private Vec3 position;

    @Shadow
    private Level level;

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract boolean onGround();

    @Shadow
    public abstract float maxUpStep();

    @Shadow
    public abstract Level level();

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Shadow
    protected abstract double getEffectiveGravity();

    @Shadow
    public boolean verticalCollision;

    @Redirect(method = "move", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;horizontalCollision:Z", ordinal = 2, opcode = Opcodes.GETFIELD))
    private boolean removeVerticalCheck(Entity instance) {
        return instance.horizontalCollision || (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1) && this.verticalCollision);
    }

    @Redirect(method = {"restituteMovementAfterCollisions(Lnet/minecraft/world/level/block/state/BlockState;ZZLnet/minecraft/world/phys/Vec3;)V", "restituteMovementAfterCollisions(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;ZZLnet/minecraft/world/phys/Vec3;)V"}, at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;verticalCollisionBelow:Z", opcode = Opcodes.GETFIELD))
    private boolean fixBelowCollisionCheck(Entity instance) {
        return instance.verticalCollisionBelow || ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1);
    }

    @Redirect(method = {"restituteMovementAfterCollisions(Lnet/minecraft/world/level/block/state/BlockState;ZZLnet/minecraft/world/phys/Vec3;)V", "restituteMovementAfterCollisions(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;ZZLnet/minecraft/world/phys/Vec3;)V"}, at = @At(value = "FIELD", target = "Lnet/minecraft/world/phys/Vec3;y:D", ordinal = 0))
    private double fixGravityCheck(Vec3 currentMovement) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            return -currentMovement.y - this.getEffectiveGravity();
        } else {
            return currentMovement.y;
        }
    }

    @Redirect(method = {"restituteMovementAfterCollisions(Lnet/minecraft/world/level/block/state/BlockState;ZZLnet/minecraft/world/phys/Vec3;)V", "restituteMovementAfterCollisions(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;ZZLnet/minecraft/world/phys/Vec3;)V"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;with(Lnet/minecraft/core/Direction$Axis;D)Lnet/minecraft/world/phys/Vec3;", ordinal = 2))
    private Vec3 fixRestitution(Vec3 instance, Direction.Axis axis, double value, @Local(name = "restitution") double restitution) {
        return instance.with(axis, ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1) ? -this.getDeltaMovement().y * restitution : value);
    }

    @WrapWithCondition(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;addMovementThisTick(Lnet/minecraft/world/entity/Entity$Movement;)V"))
    private boolean removeExtraCollisionChecks(Entity instance, Entity.Movement movement) {
        return ProtocolTranslator.getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_5);
    }

    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;lengthSqr()D", ordinal = 1), slice = @Slice(
            from = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;")
    ))
    private double allowSmallValues(Vec3 instance) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {
            return Double.MAX_VALUE;
        } else {
            return instance.lengthSqr();
        }
    }

    @Inject(method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void use1_20_6StepCollisionCalculation(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            final Entity thiz = (Entity) (Object) this;
            final AABB box = this.getBoundingBox();
            final List<VoxelShape> collisions = this.level().getEntityCollisions(thiz, box.expandTowards(movement));
            Vec3 adjustedMovement = movement.lengthSqr() == 0D ? movement : Entity.collideBoundingBox(thiz, movement, box, this.level(), collisions);
            final boolean changedX = movement.x != adjustedMovement.x;
            final boolean changedY = movement.y != adjustedMovement.y;
            final boolean changedZ = movement.z != adjustedMovement.z;
            final boolean mayTouchGround = this.onGround() || changedY && movement.y < 0D;
            if (this.maxUpStep() > 0F && mayTouchGround && (changedX || changedZ)) {
                Vec3 vec3d2 = Entity.collideBoundingBox(thiz, new Vec3(movement.x, this.maxUpStep(), movement.z), box, this.level(), collisions);
                Vec3 vec3d3 = Entity.collideBoundingBox(thiz, new Vec3(0D, this.maxUpStep(), 0D), box.expandTowards(movement.x, 0D, movement.z), this.level(), collisions);
                if (vec3d3.y < this.maxUpStep()) {
                    Vec3 vec3d4 = Entity.collideBoundingBox(thiz, new Vec3(movement.x, 0D, movement.z), box.move(vec3d3), this.level(), collisions).add(vec3d3);
                    if (vec3d4.horizontalDistanceSqr() > vec3d2.horizontalDistanceSqr()) {
                        vec3d2 = vec3d4;
                    }
                }

                if (vec3d2.horizontalDistanceSqr() > adjustedMovement.horizontalDistanceSqr()) {
                    adjustedMovement = vec3d2.add(Entity.collideBoundingBox(thiz, new Vec3(0D, -vec3d2.y + (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2) ? 0 : movement.y), 0D), box.move(vec3d2), this.level(), collisions));
                }
            }

            cir.setReturnValue(adjustedMovement);
        }
    }

    @Inject(method = "getOnPos(F)Lnet/minecraft/core/BlockPos;", at = @At("HEAD"), cancellable = true)
    private void modifyPosWithYOffset(float offset, CallbackInfoReturnable<BlockPos> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19_4)) {
            int i = Mth.floor(this.position.x);
            int j = Mth.floor(this.position.y - (double) (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_18_2) && offset == 1.0E-5F ? 0.2F : offset));
            int k = Mth.floor(this.position.z);
            BlockPos blockPos = new BlockPos(i, j, k);
            if (this.level.getBlockState(blockPos).isAir()) {
                BlockPos downPos = blockPos.below();
                BlockState blockState = this.level.getBlockState(downPos);
                if (blockState.is(BlockTags.FENCES) || blockState.is(BlockTags.WALLS) || blockState.getBlock() instanceof FenceGateBlock) {
                    cir.setReturnValue(downPos);
                    return;
                }
            }

            cir.setReturnValue(blockPos);
        }
    }

    @Inject(method = "getBlockPosBelowThatAffectsMyMovement", at = @At("HEAD"), cancellable = true)
    private void modifyVelocityAffectingPos(CallbackInfoReturnable<BlockPos> cir) {
        final ProtocolVersion target = ProtocolTranslator.getTargetVersion();

        if (target.olderThanOrEqualTo(ProtocolVersion.v1_19_4)) {
            cir.setReturnValue(BlockPos.containing(position.x, getBoundingBox().minY - (target.olderThanOrEqualTo(ProtocolVersion.v1_14_4) ? 1 : 0.5000001), position.z));
        }
    }

    @Redirect(method = {"collideWithShapes(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;", "checkInsideBlocks(Ljava/util/List;Lnet/minecraft/world/entity/InsideBlockEffectApplier$StepBasedCollector;)V"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;axisStepOrder(Lnet/minecraft/world/phys/Vec3;)Lcom/google/common/collect/ImmutableList;"))
    private static ImmutableList<Direction.Axis> alwaysSortYXZ(Vec3 movement) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            return Direction.YXZ_AXIS_ORDER;
        } else {
            return Direction.axisStepOrder(movement);
        }
    }

    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;equal(DD)Z"))
    private static boolean horizontalExactCollisionEqualness(double a, double b) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            return a == b;
        } else {
            return Mth.equal(a, b);
        }
    }

}
