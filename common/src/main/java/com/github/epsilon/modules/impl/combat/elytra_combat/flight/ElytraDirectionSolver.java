package com.github.epsilon.modules.impl.combat.elytra_combat.flight;

import com.github.epsilon.utils.rotation.Rot2f;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 将期望速度反解为 input 模式可用的 yaw/pitch。
 *
 * <p>因为滑翔速度受惯性、重力和阻力影响，直接看向目标并不等于实际飞向目标。
 * 这里固定 yaw 为期望水平方向，在 pitch 范围内采样并细化，使下一 tick 模拟速度与期望速度夹角最小。</p>
 */
public final class ElytraDirectionSolver {

    /** 安全解至少预演的 tick 数；短于该值的候选会被视为存在近期碰撞风险。 */
    private static final int TRAJECTORY_HORIZON_TICKS = 4;
    /** 抬头保护探测距离与逃逸 pitch；与 ControlElytraFlightMode 保持一致。 */
    private static final double CEILING_PROBE_DISTANCE = 0.75;
    private static final double CEILING_PROBE_EPSILON = 1.0E-4;
    private static final float CEILING_ESCAPE_PITCH = 5.0f;
    private static final List<RotationOffset> ESCAPE_OFFSETS = createEscapeOffsets();

    private ElytraDirectionSolver() {
    }

    /**
     * 主线程使用的安全解。
     *
     * <p>先按速度对齐求出基础旋转，再用完整玩家碰撞箱沿 26.2 滑翔方程预演后续
     * tick。只要预演会碰到方块（包括头顶），就在基础解附近寻找差值最小、仍能通过
     * 完整预演的旋转，避免“下体过去但头顶撞方块”。</p>
     */
    public static Rot2f solveSafe(LocalPlayer player, Vec3 desiredVelocity) {
        if (desiredVelocity.lengthSqr() < 1.0E-8) {
            return new Rot2f(player.getYRot(), player.getXRot());
        }

        boolean ceilingEscape = shouldAvoidCeilingLift(player);
        Rot2f base = applyCeilingEscape(solve(player, desiredVelocity), ceilingEscape);
        int baseSafeTicks = trajectorySafeTicks(player, base.getYaw(), base.getPitch());
        if (baseSafeTicks >= TRAJECTORY_HORIZON_TICKS) {
            return base;
        }

        float fallbackYaw = base.getYaw();
        float fallbackPitch = base.getPitch();
        int fallbackSafeTicks = baseSafeTicks;
        for (RotationOffset offset : ESCAPE_OFFSETS) {
            float yaw = Mth.wrapDegrees(base.getYaw() + offset.yawOffset());
            float pitch = applyCeilingEscape(
                    Mth.clamp(base.getPitch() + offset.pitchOffset(), -89.0f, 89.0f),
                    ceilingEscape
            );
            int safeTicks = trajectorySafeTicks(player, yaw, pitch);
            if (safeTicks >= TRAJECTORY_HORIZON_TICKS) {
                return new Rot2f(yaw, pitch);
            }
            if (safeTicks > fallbackSafeTicks) {
                fallbackSafeTicks = safeTicks;
                fallbackYaw = yaw;
                fallbackPitch = pitch;
            }
        }
        return new Rot2f(fallbackYaw, fallbackPitch);
    }

    /**
     * 与 input 模式真实执行一致的纯数学解，工作线程的路径预演也调用这一版本。
     */
    public static Rot2f solve(LocalPlayer player, Vec3 desiredVelocity) {
        if (desiredVelocity.lengthSqr() < 1.0E-8) {
            return new Rot2f(player.getYRot(), player.getXRot());
        }
        return solve(player.getDeltaMovement(), effectiveGravity(player), desiredVelocity);
    }

    public static Rot2f solve(Vec3 movement, double gravity, Vec3 desiredVelocity) {
        if (desiredVelocity.lengthSqr() < 1.0E-8) {
            return new Rot2f(0.0f, 0.0f);
        }

        // yaw 直接取期望方向；pitch 先 5 度粗采样，再在最优区间二分细化。
        Vec3 desiredDirection = desiredVelocity.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(desiredDirection.z, desiredDirection.x)) - 90.0f;
        yaw = Mth.wrapDegrees(yaw);

        float bestPitch = Mth.clamp(pitchOf(desiredDirection), -89.0f, 89.0f);
        double bestDot = Double.NEGATIVE_INFINITY;
        for (float pitch = -80.0f; pitch <= 80.0f; pitch += 5.0f) {
            double dot = velocityAlignment(movement, gravity, desiredDirection, yaw, pitch);
            if (dot > bestDot) {
                bestDot = dot;
                bestPitch = pitch;
            }
        }

        float low = Math.max(-89.0f, bestPitch - 5.0f);
        float high = Math.min(89.0f, bestPitch + 5.0f);
        for (int i = 0; i < 20; i++) {
            float mid = (low + high) * 0.5f;
            double left = velocityAlignment(movement, gravity, desiredDirection, yaw, mid - 0.001f);
            double right = velocityAlignment(movement, gravity, desiredDirection, yaw, mid + 0.001f);
            if (left > right) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return new Rot2f(yaw, Mth.clamp((low + high) * 0.5f, -89.0f, 89.0f));
    }

    private static double velocityAlignment(
            Vec3 movement,
            double gravity,
            Vec3 desiredDirection,
            float yaw,
            float pitch
    ) {
        // 比较的是“下一 tick 滑翔方程输出速度”的方向，而不是实体当前 look。
        Vec3 predicted = ElytraMotionPredictor.nextFallFlyingMovement(
                movement,
                yaw,
                pitch,
                gravity
        );
        if (predicted.lengthSqr() < 1.0E-8) {
            return Double.NEGATIVE_INFINITY;
        }
        return predicted.normalize().dot(desiredDirection);
    }

    private static int trajectorySafeTicks(LocalPlayer player, float yaw, float pitch) {
        // 用完整玩家 AABB 逐步推进滑翔方程；返回首次碰撞前的安全 tick 数。
        Vec3 position = player.position();
        Vec3 velocity = player.getDeltaMovement();
        double gravity = effectiveGravity(player);
        for (int tick = 0; tick < TRAJECTORY_HORIZON_TICKS; tick++) {
            Vec3 nextVelocity = ElytraMotionPredictor.nextFallFlyingMovement(velocity, yaw, pitch, gravity);
            Vec3 nextPosition = position.add(nextVelocity);
            if (!LocalFlightAvoidance.isSegmentClear(player, position, nextPosition)) {
                return tick;
            }
            position = nextPosition;
            velocity = nextVelocity;
        }
        return TRAJECTORY_HORIZON_TICKS;
    }

    /**
     * 与 ControlElytraFlightMode 的抬头保护保持一致；这里在求解候选旋转时就应用，
     * 保证最终交给飞控的 pitch 已经参与过轨迹预演。
     */
    private static boolean shouldAvoidCeilingLift(LocalPlayer player) {
        if (!player.isFallFlying()) {
            return false;
        }

        AABB box = player.getBoundingBox();
        double probeDistance = CEILING_PROBE_DISTANCE + Math.max(0.0, player.getDeltaMovement().y);
        AABB ceilingProbe = new AABB(
                box.minX + CEILING_PROBE_EPSILON,
                box.maxY - CEILING_PROBE_EPSILON,
                box.minZ + CEILING_PROBE_EPSILON,
                box.maxX - CEILING_PROBE_EPSILON,
                box.maxY + probeDistance,
                box.maxZ - CEILING_PROBE_EPSILON
        );
        return !player.level().noBlockCollision(player, ceilingProbe);
    }

    private static Rot2f applyCeilingEscape(Rot2f rotation, boolean ceilingEscape) {
        if (!ceilingEscape || rotation.getPitch() >= CEILING_ESCAPE_PITCH) {
            return rotation;
        }
        return new Rot2f(rotation.getYaw(), CEILING_ESCAPE_PITCH);
    }

    private static float applyCeilingEscape(float pitch, boolean ceilingEscape) {
        return ceilingEscape ? Math.max(pitch, CEILING_ESCAPE_PITCH) : pitch;
    }

    private static float pitchOf(Vec3 direction) {
        double horizontal = Math.max(0.001, direction.horizontalDistance());
        return (float) -Math.toDegrees(Math.atan2(direction.y, horizontal));
    }

    private static double effectiveGravity(LocalPlayer player) {
        // 与原版 LivingEntity.getEffectiveGravity 一致：下落且缓降时重力和 0.01 取小。
        if (player.getDeltaMovement().y <= 0.0 && player.hasEffect(MobEffects.SLOW_FALLING)) {
            return Math.min(player.getGravity(), 0.01);
        }
        return player.getGravity();
    }

    private static List<RotationOffset> createEscapeOffsets() {
        // 候选按偏移代价排序：优先尝试最小偏航/俯仰修正，实在不行再大幅转向。
        float[] yawOffsets = {0.0f, 20.0f, -20.0f, 40.0f, -40.0f, 65.0f, -65.0f, 90.0f, -90.0f};
        float[] pitchOffsets = {0.0f, 15.0f, -15.0f, 30.0f, -30.0f, 50.0f, -50.0f, 75.0f, -75.0f};
        List<RotationOffset> offsets = new ArrayList<>(yawOffsets.length * pitchOffsets.length - 1);
        for (float yaw : yawOffsets) {
            for (float pitch : pitchOffsets) {
                if (yaw == 0.0f && pitch == 0.0f) {
                    continue;
                }
                offsets.add(new RotationOffset(yaw, pitch, Math.abs(yaw) + Math.abs(pitch) * 0.75f));
            }
        }
        offsets.sort(Comparator.comparingDouble(RotationOffset::penalty));
        return List.copyOf(offsets);
    }

    private record RotationOffset(float yawOffset, float pitchOffset, double penalty) {
    }
}
