package com.github.epsilon.modules.impl.combat.elytra_combat.flight;

import com.github.epsilon.modules.impl.combat.elytra_combat.path.VoxelCollisionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * 使用完整玩家碰撞箱和 26.2 滑翔运动方程校验路径。
 */
public final class ElytraMotionPredictor {

    private static final double HORIZONTAL_CLEARANCE = 0.18;
    private static final double VERTICAL_CLEARANCE = 0.10;
    private static final int MAX_PREDICTION_TICKS = 10;
    private static final double WAYPOINT_REACHED_SQR = 0.75 * 0.75;

    private ElytraMotionPredictor() {
    }

    public record PlayerCollisionProfile(double width, double height) {
    }

    public record SweepResult(boolean safe, long blockingBlock) {

        static SweepResult passed() {
            return new SweepResult(true, VoxelCollisionCache.NO_BLOCK);
        }

        static SweepResult blocked(long blockingBlock) {
            return new SweepResult(false, blockingBlock);
        }
    }

    public record ValidationResult(boolean safe, int lastSafePathIndex, long blockingBlock) {

        static ValidationResult safe(int lastSafePathIndex) {
            return new ValidationResult(true, lastSafePathIndex, VoxelCollisionCache.NO_BLOCK);
        }

        static ValidationResult unsafe(int lastSafePathIndex, long blockingBlock) {
            return new ValidationResult(false, lastSafePathIndex, blockingBlock);
        }
    }

    public static boolean isSweepClear(
            VoxelCollisionCache grid,
            PlayerCollisionProfile profile,
            Vec3 from,
            Vec3 to,
            Set<Long> extraBlocked
    ) {
        return checkSweep(grid, profile, from, to, extraBlocked).safe();
    }

    public static SweepResult checkSweep(
            VoxelCollisionCache grid,
            PlayerCollisionProfile profile,
            Vec3 from,
            Vec3 to,
            Set<Long> extraBlocked
    ) {
        if (!from.isFinite() || !to.isFinite()) {
            return SweepResult.blocked(VoxelCollisionCache.OUTSIDE_WINDOW);
        }

        double halfWidth = profile.width() * 0.5 + HORIZONTAL_CLEARANCE;

        double minX = Math.min(from.x, to.x) - halfWidth;
        double maxX = Math.max(from.x, to.x) + halfWidth;
        double minY = Math.min(from.y, to.y) - VERTICAL_CLEARANCE;
        double maxY = Math.max(from.y, to.y) + profile.height() + VERTICAL_CLEARANCE;
        double minZ = Math.min(from.z, to.z) - halfWidth;
        double maxZ = Math.max(from.z, to.z) + halfWidth;

        AABB sweptBounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        if (!extraBlocked.isEmpty()) {
            for (long packedBlock : extraBlocked) {
                if (new AABB(BlockPos.of(packedBlock)).intersects(sweptBounds)) {
                    return SweepResult.blocked(packedBlock);
                }
            }
        }

        int minBlockX = Mth.floor(minX);
        int minBlockY = Mth.floor(minY);
        int minBlockZ = Mth.floor(minZ);
        int maxBlockX = Mth.ceil(maxX) - 1;
        int maxBlockY = Mth.ceil(maxY) - 1;
        int maxBlockZ = Mth.ceil(maxZ) - 1;

        long blockingBlock = grid.findBlockedInVolume(
                minBlockX,
                minBlockY,
                minBlockZ,
                maxBlockX,
                maxBlockY,
                maxBlockZ
        );
        return blockingBlock == VoxelCollisionCache.NO_BLOCK
                ? SweepResult.passed()
                : SweepResult.blocked(blockingBlock);
    }

    public static ValidationResult validatePath(
            VoxelCollisionCache grid,
            PlayerCollisionProfile profile,
            Vec3 start,
            Vec3 initialVelocity,
            List<Vec3> points,
            double gravity
    ) {
        if (points.size() < 2) {
            return ValidationResult.safe(0);
        }

        Vec3 position = start;
        Vec3 velocity = initialVelocity;
        int nextPoint = 1;
        int lastSafePoint = 0;
        Set<Long> noExtraObstacles = Set.of();

        for (int tick = 0; tick < MAX_PREDICTION_TICKS; tick++) {
            while (nextPoint < points.size()
                    && position.distanceToSqr(points.get(nextPoint)) <= WAYPOINT_REACHED_SQR) {
                lastSafePoint = nextPoint;
                nextPoint++;
            }
            if (nextPoint >= points.size()) {
                return ValidationResult.safe(lastSafePoint);
            }

            Vec3 delta = points.get(nextPoint).subtract(position);
            float yaw = yawTo(delta);
            float pitch = pitchTo(delta);
            Vec3 nextVelocity = nextFallFlyingMovement(velocity, yaw, pitch, gravity);
            Vec3 nextPosition = position.add(nextVelocity);

            SweepResult sweep = checkSweep(grid, profile, position, nextPosition, noExtraObstacles);
            if (!sweep.safe()) {
                return ValidationResult.unsafe(lastSafePoint, sweep.blockingBlock());
            }

            position = nextPosition;
            velocity = nextVelocity;
        }

        return ValidationResult.safe(lastSafePoint);
    }

    public static Vec3 nextFallFlyingMovement(Vec3 movement, float yaw, float pitch, double gravity) {
        Vec3 lookAngle = calculateViewVector(pitch, yaw);
        double leanAngle = pitch * (Math.PI / 180.0);
        double lookHorizontalLength = Math.sqrt(lookAngle.x * lookAngle.x + lookAngle.z * lookAngle.z);
        double movementHorizontalLength = movement.horizontalDistance();
        double liftForce = Math.cos(leanAngle) * Math.cos(leanAngle);

        movement = movement.add(0.0, gravity * (-1.0 + liftForce * 0.75), 0.0);
        if (movement.y < 0.0 && lookHorizontalLength > 0.0) {
            double convert = movement.y * -0.1 * liftForce;
            movement = movement.add(
                    lookAngle.x * convert / lookHorizontalLength,
                    convert,
                    lookAngle.z * convert / lookHorizontalLength
            );
        }

        if (leanAngle < 0.0 && lookHorizontalLength > 0.0) {
            double convert = movementHorizontalLength * -Math.sin(leanAngle) * 0.04;
            movement = movement.add(
                    -lookAngle.x * convert / lookHorizontalLength,
                    convert * 3.2,
                    -lookAngle.z * convert / lookHorizontalLength
            );
        }

        if (lookHorizontalLength > 0.0) {
            movement = movement.add(
                    (lookAngle.x / lookHorizontalLength * movementHorizontalLength - movement.x) * 0.1,
                    0.0,
                    (lookAngle.z / lookHorizontalLength * movementHorizontalLength - movement.z) * 0.1
            );
        }

        return movement.multiply(0.99, 0.98, 0.99);
    }

    private static Vec3 calculateViewVector(float pitch, float yaw) {
        float realPitch = pitch * (float) (Math.PI / 180.0);
        float realYaw = -yaw * (float) (Math.PI / 180.0);
        float yawCos = Mth.cos(realYaw);
        float yawSin = Mth.sin(realYaw);
        float pitchCos = Mth.cos(realPitch);
        float pitchSin = Mth.sin(realPitch);
        return new Vec3(yawSin * pitchCos, -pitchSin, yawCos * pitchCos);
    }

    public static float yawTo(Vec3 delta) {
        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f);
    }

    public static float pitchTo(Vec3 delta) {
        double horizontal = Math.max(0.001, delta.horizontalDistance());
        return Mth.clamp((float) -Math.toDegrees(Math.atan2(delta.y, horizontal)), -90.0f, 90.0f);
    }
}
