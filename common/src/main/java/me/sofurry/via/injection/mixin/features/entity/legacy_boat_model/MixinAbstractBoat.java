package me.sofurry.via.injection.mixin.features.entity.legacy_boat_model;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.features.entity.legacy_boat_model.PositionInterpolator1_8;
import me.sofurry.via.injection.access.entity.legacy_boat_model.IAbstractBoat;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBoat.class)
public abstract class MixinAbstractBoat extends VehicleEntity implements IAbstractBoat {

    @Shadow
    private Boat.Status status;

    @Shadow
    public abstract InterpolationHandler getInterpolation();

    @Unique
    private final InterpolationHandler viaVersion$positionInterpolator = new PositionInterpolator1_8((AbstractBoat) (Object) this);

    @Unique
    private double viaVersion$speedMultiplier = 0.07D;

    @Unique
    private int viaVersion$boatInterpolationSteps;

    @Unique
    private Vec3 viaVersion$boatVelocity = Vec3.ZERO;

    public MixinAbstractBoat(EntityType<?> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "push", at = @At("HEAD"), cancellable = true)
    private void pushAwayFrom1_8(Entity entity, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            ci.cancel();
            super.push(entity);
        }
    }

    @Inject(method = "getInterpolation", at = @At("HEAD"), cancellable = true)
    private void replaceInterpolation(CallbackInfoReturnable<InterpolationHandler> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            cir.setReturnValue(viaVersion$positionInterpolator);
        }
    }

    @Override
    public void lerpMotion(final @NonNull Vec3 clientVelocity) {
        super.lerpMotion(clientVelocity);

        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            this.viaVersion$boatVelocity = clientVelocity;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void tick1_8(CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            ci.cancel();
            super.tick();

            if (this.getHurtTime() > 0) {
                this.setHurtTime(this.getHurtTime() - 1);
            }
            if (this.getDamage() > 0) {
                this.setDamage(this.getDamage() - 1);
            }
            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();

            final int yPartitions = 5;
            double percentSubmerged = 0;
            for (int partitionIndex = 0; partitionIndex < yPartitions; partitionIndex++) {
                final double minY = this.getBoundingBox().minY + this.getBoundingBox().getYsize() * partitionIndex / yPartitions - 0.125;
                final double maxY = this.getBoundingBox().minY + this.getBoundingBox().getYsize() * (partitionIndex + 1) / yPartitions - 0.125;
                final AABB box = new AABB(this.getBoundingBox().minX, minY, this.getBoundingBox().minZ, this.getBoundingBox().maxX, maxY, this.getBoundingBox().maxZ);
                if (BlockPos.betweenClosedStream(box).anyMatch(pos -> this.level().getFluidState(pos).is(FluidTags.WATER))) {
                    percentSubmerged += 1.0 / yPartitions;
                }
            }

            final double oldHorizontalSpeed = this.getDeltaMovement().horizontalDistance();
            if (oldHorizontalSpeed > (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_7_6) ? 0.2625D : 0.2975D)) {
                final double rx = Math.cos(this.getYRot() * Math.PI / 180);
                final double rz = Math.sin(this.getYRot() * Math.PI / 180);
                for (int i = 0; i < 1 + oldHorizontalSpeed * 60; i++) {
                    final double dForward = this.random.nextFloat() * 2 - 1;
                    final double dSideways = (this.random.nextInt(2) * 2 - 1) * 0.7D;
                    if (this.random.nextBoolean()) {
                        final double x = this.getX() - rx * dForward * 0.8 + rz * dSideways;
                        final double z = this.getZ() - rz * dForward * 0.8 - rx * dSideways;
                        this.level().addParticle(ParticleTypes.SPLASH, x, this.getY() - 0.125D, z, this.getDeltaMovement().x, this.getDeltaMovement().y, this.getDeltaMovement().z);
                    } else {
                        final double x = this.getX() + rx + rz * dForward * 0.7;
                        final double z = this.getZ() + rz - rx * dForward * 0.7;
                        this.level().addParticle(ParticleTypes.SPLASH, x, this.getY() - 0.125D, z, this.getDeltaMovement().x, this.getDeltaMovement().y, this.getDeltaMovement().z);
                    }
                }
            }

            if (this.level().isClientSide() && !this.isVehicle()) {
                if (this.viaVersion$boatInterpolationSteps > 0) {
                    final InterpolationHandler.InterpolationData data = viaVersion$positionInterpolator.interpolationData;
                    final double newX = this.getX() + (data.position.x - this.getX()) / this.viaVersion$boatInterpolationSteps;
                    final double newY = this.getY() + (data.position.y - this.getY()) / this.viaVersion$boatInterpolationSteps;
                    final double newZ = this.getZ() + (data.position.z - this.getZ()) / this.viaVersion$boatInterpolationSteps;
                    final double newYaw = this.getYRot() + Mth.wrapDegrees(data.yRot - this.getYRot()) / this.viaVersion$boatInterpolationSteps;
                    final double newPitch = this.getXRot() + (data.xRot - this.getXRot()) / this.viaVersion$boatInterpolationSteps;
                    this.viaVersion$boatInterpolationSteps--;
                    this.setPos(newX, newY, newZ);
                    this.setRot((float) newYaw, (float) newPitch);
                } else {
                    this.setPos(this.getX() + this.getDeltaMovement().x, this.getY() + this.getDeltaMovement().y, this.getZ() + this.getDeltaMovement().z);
                    if (this.onGround()) {
                        this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
                    }
                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.99D, 0.95D, 0.99D));
                }
            } else {
                if (percentSubmerged < 1) {
                    final double normalizedDistanceFromMiddle = percentSubmerged * 2 - 1;
                    this.setDeltaMovement(this.getDeltaMovement().add(0, 0.04D * normalizedDistanceFromMiddle, 0));
                } else {
                    if (this.getDeltaMovement().y < 0) {
                        this.setDeltaMovement(this.getDeltaMovement().multiply(1, 0.5D, 1));
                    }
                    this.setDeltaMovement(this.getDeltaMovement().add(0, 0.007D, 0));
                }

                if (this.getControllingPassenger() != null) {
                    final LivingEntity passenger = this.getControllingPassenger();
                    if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_5_2)) {
                        final double xAcceleration = passenger.getDeltaMovement().x * this.viaVersion$speedMultiplier;
                        final double zAcceleration = passenger.getDeltaMovement().z * this.viaVersion$speedMultiplier;
                        this.setDeltaMovement(this.getDeltaMovement().add(xAcceleration, 0, zAcceleration));
                    } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_6_4)) {
                        if (passenger.zza > 0) {
                            final double xAcceleration = -Math.sin(passenger.getYRot() * Math.PI / 180) * this.viaVersion$speedMultiplier * 0.05D;
                            final double zAcceleration = Math.cos(passenger.getYRot() * Math.PI / 180) * this.viaVersion$speedMultiplier * 0.05D;
                            this.setDeltaMovement(this.getDeltaMovement().add(xAcceleration, 0, zAcceleration));
                        }
                    } else {
                        final float boatAngle = passenger.getYRot() - passenger.xxa * 90F;
                        final double xAcceleration = -Math.sin(boatAngle * Math.PI / 180) * this.viaVersion$speedMultiplier * passenger.zza * 0.05D;
                        final double zAcceleration = Math.cos(boatAngle * Math.PI / 180) * this.viaVersion$speedMultiplier * passenger.zza * 0.05D;
                        this.setDeltaMovement(this.getDeltaMovement().add(xAcceleration, 0, zAcceleration));
                    }
                }

                double newHorizontalSpeed = this.getDeltaMovement().horizontalDistance();
                if (newHorizontalSpeed > 0.35D) {
                    final double multiplier = 0.35D / newHorizontalSpeed;
                    this.setDeltaMovement(this.getDeltaMovement().multiply(multiplier, 1, multiplier));
                    newHorizontalSpeed = 0.35D;
                }

                if (newHorizontalSpeed > oldHorizontalSpeed && this.viaVersion$speedMultiplier < 0.35D) {
                    this.viaVersion$speedMultiplier += (0.35D - this.viaVersion$speedMultiplier) / 35;
                    if (this.viaVersion$speedMultiplier > 0.35D) {
                        this.viaVersion$speedMultiplier = 0.35D;
                    }
                } else {
                    this.viaVersion$speedMultiplier -= (this.viaVersion$speedMultiplier - 0.07D) / 35;
                    if (this.viaVersion$speedMultiplier < 0.07D) {
                        this.viaVersion$speedMultiplier = 0.07D;
                    }
                }

                if (ProtocolTranslator.getTargetVersion().newerThan(LegacyProtocolVersion.r1_6_4)) {
                    for (int i = 0; i < 4; i++) {
                        final int dx = Mth.floor(this.getX() + ((i % 2) - 0.5D) * 0.8D);
                        //noinspection IntegerDivisionInFloatingPointContext
                        final int dz = Mth.floor(this.getZ() + ((i / 2) - 0.5D) * 0.8D);
                        for (int ddy = 0; ddy < 2; ddy++) {
                            final int dy = Mth.floor(this.getY()) + ddy;
                            final BlockPos pos = new BlockPos(dx, dy, dz);
                            final Block block = this.level().getBlockState(pos).getBlock();
                            if (block == Blocks.SNOW) {
                                this.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                                this.horizontalCollision = false;
                            } else if (block == Blocks.LILY_PAD) {
                                this.level().destroyBlock(pos, true);
                                this.horizontalCollision = false;
                            }
                        }
                    }
                }

                if (this.onGround()) {
                    this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
                }

                this.move(MoverType.SELF, this.getDeltaMovement());

                if (!this.horizontalCollision || oldHorizontalSpeed <= 0.2975D) {
                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.99D, 0.95D, 0.99D));
                }

                this.setXRot(0);
                final double deltaX = this.xo - this.getX();
                final double deltaZ = this.zo - this.getZ();
                if (deltaX * deltaX + deltaZ * deltaZ > 0.001D) {
                    final double yawDelta = Mth.clamp(Mth.wrapDegrees((Mth.atan2(deltaZ, deltaX) * 180 / Math.PI) - this.getYRot()), -20, 20);
                    this.setYRot((float) (this.getYRot() + yawDelta));
                }
            }
        }
    }

    @Inject(method = "onPassengerTurned", at = @At("HEAD"), cancellable = true)
    private void onPassengerLookAround1_8(Entity passenger, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            ci.cancel();
            super.onPassengerTurned(passenger);
        }
    }

    @Inject(method = "checkFallDamage", at = @At("HEAD"), cancellable = true)
    private void fall1_8(double ya, boolean onGround, BlockState onState, BlockPos pos, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_6_4)) {
            ci.cancel();
            super.checkFallDamage(ya, onGround, onState, pos);
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            this.status = Boat.Status.ON_LAND;
        }
    }

    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    private void canAddPassenger1_8(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            cir.setReturnValue(super.canAddPassenger(passenger));
        }
    }

    @Override
    public void viaVersion$setBoatInterpolationSteps(final int steps) {
        viaVersion$boatInterpolationSteps = steps;
    }

    @Override
    public Vec3 viaVersion$getBoatVelocity() {
        return viaVersion$boatVelocity;
    }

    @Override
    public void viaVersion$setBoatVelocity(final Vec3 velocity) {
        viaVersion$boatVelocity = velocity;
    }

}
