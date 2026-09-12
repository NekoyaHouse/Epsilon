package com.github.epsilon.modules.impl.combat.elytra_combat.flight;

import com.github.epsilon.utils.rotation.Rot2f;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * 将期望速度反解为 input 模式可用的 yaw/pitch。
 *
 * <p>因为滑翔速度受惯性、重力和阻力影响，直接看向目标并不等于实际飞向目标。
 * 这里固定 yaw 为期望水平方向，在 pitch 范围内采样并细化，使下一 tick 模拟速度与期望速度夹角最小。</p>
 */
public final class ElytraDirectionSolver {

    private ElytraDirectionSolver() {
    }

    public static Rot2f solve(LocalPlayer player, Vec3 desiredVelocity) {
        if (desiredVelocity.lengthSqr() < 1.0E-8) {
            return new Rot2f(player.getYRot(), player.getXRot());
        }

        Vec3 desiredDirection = desiredVelocity.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(desiredDirection.z, desiredDirection.x)) - 90.0f;
        yaw = Mth.wrapDegrees(yaw);
        double gravity = effectiveGravity(player);

        float bestPitch = Mth.clamp(pitchOf(desiredDirection), -89.0f, 89.0f);
        double bestDot = Double.NEGATIVE_INFINITY;
        for (float pitch = -80.0f; pitch <= 80.0f; pitch += 5.0f) {
            double dot = velocityAlignment(player, desiredDirection, yaw, pitch, gravity);
            if (dot > bestDot) {
                bestDot = dot;
                bestPitch = pitch;
            }
        }

        float low = Math.max(-89.0f, bestPitch - 5.0f);
        float high = Math.min(89.0f, bestPitch + 5.0f);
        for (int i = 0; i < 20; i++) {
            float mid = (low + high) * 0.5f;
            double left = velocityAlignment(player, desiredDirection, yaw, mid - 0.001f, gravity);
            double right = velocityAlignment(player, desiredDirection, yaw, mid + 0.001f, gravity);
            if (left > right) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return new Rot2f(yaw, Mth.clamp((low + high) * 0.5f, -89.0f, 89.0f));
    }

    private static double velocityAlignment(
            LocalPlayer player,
            Vec3 desiredDirection,
            float yaw,
            float pitch,
            double gravity
    ) {
        Vec3 predicted = ElytraMotionPredictor.nextFallFlyingMovement(
                player.getDeltaMovement(),
                yaw,
                pitch,
                gravity
        );
        if (predicted.lengthSqr() < 1.0E-8) {
            return Double.NEGATIVE_INFINITY;
        }
        return predicted.normalize().dot(desiredDirection);
    }

    private static float pitchOf(Vec3 direction) {
        double horizontal = Math.max(0.001, direction.horizontalDistance());
        return (float) -Math.toDegrees(Math.atan2(direction.y, horizontal));
    }

    private static double effectiveGravity(LocalPlayer player) {
        if (player.getDeltaMovement().y <= 0.0 && player.hasEffect(MobEffects.SLOW_FALLING)) {
            return Math.min(player.getGravity(), 0.01);
        }
        return player.getGravity();
    }
}
