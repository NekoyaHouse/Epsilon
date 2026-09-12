package com.github.epsilon.modules.impl.combat.elytra_combat.flight;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 主线程上的有界局部避障。
 *
 * <p>直线航段不可用时，只搜索有限的 yaw/pitch 偏移，不会在客户端线程运行完整 A*。</p>
 */
public final class LocalFlightAvoidance {

    private static final float[] YAW_OFFSETS = {-90.0f, -60.0f, -30.0f, 0.0f, 30.0f, 60.0f, 90.0f};
    private static final float[] PITCH_OFFSETS = {-40.0f, -25.0f, -10.0f, 0.0f, 10.0f, 25.0f, 40.0f};

    private LocalFlightAvoidance() {
    }

    public static Vec3 findAvoidance(
            LocalPlayer player,
            Vec3 desiredVelocity,
            Vec3 targetPoint,
            double probeDistance
    ) {
        if (desiredVelocity.lengthSqr() < 1.0E-8) {
            return null;
        }

        Vec3 targetDirection = targetPoint.subtract(player.position());
        if (targetDirection.lengthSqr() < 1.0E-8) {
            targetDirection = desiredVelocity;
        }
        targetDirection = targetDirection.normalize();

        Vec3 baseDirection = desiredVelocity.normalize();
        float baseYaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(baseDirection.z, baseDirection.x)) - 90.0f);
        float basePitch = (float) -Math.toDegrees(Math.atan2(
                baseDirection.y,
                Math.max(0.001, baseDirection.horizontalDistance())
        ));

        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (float yawOffset : YAW_OFFSETS) {
            for (float pitchOffset : PITCH_OFFSETS) {
                float yaw = Mth.wrapDegrees(baseYaw + yawOffset);
                float pitch = Mth.clamp(basePitch + pitchOffset, -89.0f, 89.0f);
                Vec3 direction = direction(yaw, pitch);
                Vec3 candidate = player.position().add(direction.scale(probeDistance));
                if (!isSegmentClear(player, player.position(), candidate)) {
                    continue;
                }

                double alignment = direction.dot(targetDirection);
                double score = alignment - Math.abs(yawOffset) * 0.002 - Math.abs(pitchOffset) * 0.001;
                if (score > bestScore) {
                    bestScore = score;
                    best = direction.scale(desiredVelocity.length());
                }
            }
        }
        return best;
    }

    public static boolean isSegmentClear(LocalPlayer player, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() < 1.0E-8) {
            return true;
        }

        AABB playerBox = player.getBoundingBox().deflate(1.0E-4);
        AABB swept = playerBox.expandTowards(delta);
        if (!player.level().noBorderCollision(player, playerBox.move(delta))) {
            return false;
        }
        for (var shape : player.level().getBlockCollisions(player, swept)) {
            if (playerBox.collidedAlongVector(delta, shape.toAabbs())) {
                return false;
            }
        }
        return true;
    }

    private static Vec3 direction(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double horizontal = Math.cos(pitchRad);
        return new Vec3(
                -Math.sin(yawRad) * horizontal,
                -Math.sin(pitchRad),
                Math.cos(yawRad) * horizontal
        );
    }
}
