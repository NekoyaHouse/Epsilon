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
public class ElytraDirectionSolver {

    /**
     * 安全解至少预演的 tick 数；短于该值的候选会被视为存在近期碰撞风险。
     */
    private static final int TRAJECTORY_HORIZON_TICKS = 4;
    /**
     * 抬头保护探测距离与逃逸 pitch；与 ControlElytraFlightMode 保持一致。
     */
    private static final double CEILING_PROBE_DISTANCE = 0.75;
    private static final double CEILING_PROBE_EPSILON = 1.0E-4;
    private static final float CEILING_ESCAPE_PITCH = 5.0f;
    private static final List<RotationOffset> ESCAPE_OFFSETS = createEscapeOffsets();
    /**
     * 重新搜索逃逸解时，优先在与上一次选择夹角不超过该值的候选里挑，保持机动连续。
     */
    private static final double ESCAPE_KEEP_ANGLE = 30.0;

    /**
     * 上一次采用的逃逸旋转。
     *
     * <p>逃逸搜索里"安全解"往往不止一个（例如 +85 与 -85 都能通过 4 tick 预演），
     * 每 tick 重挑就会在等价解之间翻转，表现为上升时抬头/低头互抢。记住上一次选择并在
     * 它仍然安全时沿用它，可以把这个抖动消掉。</p>
     */
    private static Rot2f lastEscapeRotation;

    /**
     * 上一次实际采用的俯仰角。
     *
     * <p>当"当前速度方向与期望方向相反"时，速度对齐目标函数会变成一片平台，argmax 每 tick
     * 落在平台的不同位置（日志里就是 pitch 在 0 与 +85 之间跳）。只要上一 tick 的 pitch 与
     * 本轮最优解的贴合度相差很小，就沿用它。</p>
     */
    private static float lastSolvedPitch = Float.NaN;

    /** 俯仰连续性容差：贴合度差距小于该值即视为"和最优一样好"。 */
    private static final double PITCH_CONTINUITY_EPSILON = 0.05;
    /** 求解俯仰相对意图俯仰的默认最大偏离（度）；防止平台抖动决定抬头/低头。 */
    public static final float DEFAULT_PITCH_DEVIATION_LIMIT = 35.0f;
    /** 容差不超过该值时视为"精确瞄准"，跳过俯仰连续性，避免已对准的姿态被上一 tick 拖回去。 */
    private static final float PRECISE_PITCH_TOLERANCE = 5.0f;
    /**
     * 速度对齐的预演 tick 数。1 tick 会让求解器贴着当前速度不肯转向（平飞绕圈），
     * 取 4 tick 与安全预演保持同一视野。
     */
    private static final int ALIGNMENT_HORIZON_TICKS = 4;

    private ElytraDirectionSolver() {
    }

    /**
     * 清空逃逸与俯仰记忆；模块状态重置或接管结束时调用，避免沿用上一段飞行的选择。
     */
    public static void resetEscape() {
        lastEscapeRotation = null;
        lastSolvedPitch = Float.NaN;
    }

    /**
     * 记录本 tick 实际提交的俯仰角，供下一 tick 的连续性判断使用（只应由主线程调用）。
     */
    public static void rememberPitch(float pitch) {
        lastSolvedPitch = pitch;
    }

    /**
     * 主线程使用的安全解。
     *
     * <p>先按速度对齐求出基础旋转，再用完整玩家碰撞箱沿 26.3 滑翔方程预演后续
     * tick。只要预演会碰到方块（包括头顶），就在基础解附近寻找差值最小、仍能通过
     * 完整预演的旋转，避免“下体过去但头顶撞方块”。</p>
     */
    public static Rot2f solveSafe(LocalPlayer player, Vec3 desiredVelocity) {
        return solveSafe(player, desiredVelocity, DEFAULT_PITCH_DEVIATION_LIMIT);
    }

    /**
     * 主线程使用的安全解，可指定俯仰容差。
     *
     * @param pitchDeviationLimit 求解俯仰允许偏离意图俯仰的最大角度（度）。默认值给"取升力/加速度"
     *                            留了足够空间；长矛这类要求视线精确命中（kinetic 判定射线就是视线）
     *                            的场景应该传更小的值，否则俯仰偏差会直接变成脱靶。
     */
    public static Rot2f solveSafe(LocalPlayer player, Vec3 desiredVelocity, float pitchDeviationLimit) {
        if (desiredVelocity.lengthSqr() < 1.0E-8) {
            return new Rot2f(player.getYRot(), player.getXRot());
        }

        boolean ceilingEscape = shouldAvoidCeilingLift(player, desiredVelocity.y);
        Rot2f base = applyCeilingEscape(solve(player, desiredVelocity), ceilingEscape);
        base = applyDegenerateFallback(player, base, desiredVelocity, ceilingEscape);
        base = applyIntentPitchLimit(base, desiredVelocity, ceilingEscape, pitchDeviationLimit);
        // 精确瞄准（长矛）不沿用旧俯仰：applyPitchContinuity 会把已经对准的 base 换回上一 tick 的姿态，
        // 多出来的几度偏差在判定半径只有 0.6 格时足够脱靶；它本来只是为消除平台抖动而设计的。
        if (pitchDeviationLimit > PRECISE_PITCH_TOLERANCE) {
            base = applyPitchContinuity(player, base, desiredVelocity, ceilingEscape, pitchDeviationLimit);
        }
        int baseSafeTicks = trajectorySafeTicks(player, base.getYaw(), base.getPitch());
        ElytraDebug.log(ElytraDebug.SLOT_ROTATION, "rotation.solve",
                "ceiling=" + ceilingEscape
                        + " pitch=" + ElytraDebug.fmt(base.getPitch())
                        + " yaw=" + ElytraDebug.fmt(base.getYaw())
                        + " safeTicks=" + baseSafeTicks);
        if (baseSafeTicks >= TRAJECTORY_HORIZON_TICKS) {
            // 基础解安全：清掉上一段逃逸选择，避免残留影响下一次进入逃逸。
            lastEscapeRotation = null;
            return base;
        }

        // 逃逸搜索里的"安全解"通常不止一个（+85 与 -85 都可能通过预演）。如果每 tick 都重新挑，
        // 就会在等价解之间来回翻转，表现为上升时抬头/低头互抢、转头抽风。这里先沿用上一次的逃逸解，
        // 只要它本 tick 仍然安全就直接复用。
        Rot2f kept = keepPreviousEscape(player, ceilingEscape);
        if (kept != null) {
            ElytraDebug.log(ElytraDebug.SLOT_ROTATION, "rotation.escape",
                    "keep pitch=" + ElytraDebug.fmt(kept.getPitch()) + " yaw=" + ElytraDebug.fmt(kept.getYaw()));
            return kept;
        }

        // 1) 优先在"与上一次选择夹角不大"的候选里找，保持机动连续。
        if (lastEscapeRotation != null) {
            Rot2f near = searchEscape(player, base, ceilingEscape, true);
            if (near != null) {
                lastEscapeRotation = near;
                ElytraDebug.log(ElytraDebug.SLOT_ROTATION, "rotation.escape",
                        "near pitch=" + ElytraDebug.fmt(near.getPitch()) + " yaw=" + ElytraDebug.fmt(near.getYaw()));
                return near;
            }
        }

        // 2) 全局搜索：取第一个通过预演的候选，并记住它供下一 tick 沿用。
        Rot2f picked = searchEscape(player, base, ceilingEscape, false);
        if (picked != null) {
            lastEscapeRotation = picked;
            ElytraDebug.log(ElytraDebug.SLOT_ROTATION, "rotation.escape",
                    "pick pitch=" + ElytraDebug.fmt(picked.getPitch()) + " yaw=" + ElytraDebug.fmt(picked.getYaw()));
            return picked;
        }

        // 3) 没有任何候选能通过完整预演：退回"安全 tick 最多"的候选，并同样记住它。
        Rot2f fallback = bestEffortEscape(player, base, ceilingEscape);
        lastEscapeRotation = fallback;
        return fallback;
    }

    /**
     * 把求解出的俯仰限制在"意图俯仰 ± pitchDeviationLimit"以内。
     *
     * <p>速度对齐在"当前速度方向与期望方向夹角较大"时会形成平台：近乎垂直抬头和全俯冲的
     * 贴合度可以只差 0.0x（日志里的 0.63/0.65）。这点差异不该决定抬头还是低头——否则会出现
     * 意图只要 30° 爬升、实际却 85° 垂直上窜，离目标越来越远，之后再绕回来。
     * 偏离上限由调用方给出：{@link #DEFAULT_PITCH_DEVIATION_LIMIT} 为取升力/加速度留了空间，
     * 而需要视线精确命中的场景（长矛）会传更小的值。</p>
     */
    private static Rot2f applyIntentPitchLimit(Rot2f base, Vec3 desiredVelocity, boolean ceilingEscape, float pitchDeviationLimit) {
        float desiredPitch = intentPitch(desiredVelocity, ceilingEscape);
        float limited = Mth.clamp(
                base.getPitch(),
                desiredPitch - pitchDeviationLimit,
                desiredPitch + pitchDeviationLimit
        );
        if (limited == base.getPitch()) {
            return base;
        }
        ElytraDebug.log(ElytraDebug.SLOT_ROTATION, "rotation.pitchLimit",
                "base=" + ElytraDebug.fmt(base.getPitch())
                        + " desired=" + ElytraDebug.fmt(desiredPitch)
                        + " ->" + ElytraDebug.fmt(limited));
        return new Rot2f(base.getYaw(), limited);
    }

    /** 意图方向本身的俯仰角（已按抬头保护上调）。 */
    private static float intentPitch(Vec3 desiredVelocity, boolean ceilingEscape) {
        return applyCeilingEscape(pitchOf(desiredVelocity.normalize()), ceilingEscape);
    }

    /**
     * 目标函数退化时的兜底。
     *
     * <p>当前速度与期望方向相反时（例如正在爬升却要俯冲），滑翔方程在预演视野内转不过来，
     * 任何 pitch 的贴合度都是负的、彼此相差无几，argmax 没有意义；继续沿用上一 tick 的 pitch
     * 会把人锁在反向姿态里（表现为"停在天上等一会儿才俯冲"）。此时直接采用期望方向本身的俯仰，
     * 先把机头转过去，让下一 tick 的速度真正开始朝期望方向变化。</p>
     *
     * <p>这里不再对俯仰变化量设限：实测"视线尽快咬住目标"比"姿态平滑"更能提高命中率
     * （对 Max Turn Speed 360 与 45 的对比：命中 2/3 对 1/6，退化次数 2.5 倍差距），
     * 而限速会让俯仰滞后于目标、射线扫不到。姿态平滑交给 {@code applyIntentPitchLimit}
     * 与滑翔物理本身。</p>
     */
    private static Rot2f applyDegenerateFallback(LocalPlayer player, Rot2f base, Vec3 desiredVelocity, boolean ceilingEscape) {
        Vec3 desiredDirection = desiredVelocity.normalize();
        double alignment = velocityAlignment(
                player.getDeltaMovement(),
                effectiveGravity(player),
                desiredDirection,
                base.getYaw(),
                base.getPitch()
        );
        if (alignment > 0.0) {
            return base;
        }

        float desiredPitch = applyCeilingEscape(pitchOf(desiredDirection), ceilingEscape);
        ElytraDebug.log(ElytraDebug.SLOT_ROTATION, "rotation.degenerate",
                "align=" + ElytraDebug.fmt(alignment)
                        + " base=" + ElytraDebug.fmt(base.getPitch())
                        + " -> desired=" + ElytraDebug.fmt(desiredPitch));
        return new Rot2f(base.getYaw(), desiredPitch);
    }

    /**
     * 俯仰连续性：目标函数出现平台时（当前速度与期望方向相反最容易出现），
     * 只要上一 tick 的 pitch 与最优解贴合度差距在容差内，就沿用它，避免 pitch 逐 tick 乱跳。
     */
    private static Rot2f applyPitchContinuity(LocalPlayer player, Rot2f base, Vec3 desiredVelocity, boolean ceilingEscape, float pitchDeviationLimit) {
        if (Float.isNaN(lastSolvedPitch)) {
            return base;
        }

        float previousPitch = applyCeilingEscape(lastSolvedPitch, ceilingEscape);
        // 上一 tick 的姿态也要落在本 tick 的意图带内，避免意图已反向时继续沿用旧姿态。
        float bandPitch = intentPitch(desiredVelocity, ceilingEscape);
        previousPitch = Mth.clamp(
                previousPitch,
                bandPitch - pitchDeviationLimit,
                bandPitch + pitchDeviationLimit
        );
        Vec3 movement = player.getDeltaMovement();
        double gravity = effectiveGravity(player);
        Vec3 desiredDirection = desiredVelocity.normalize();
        double baseAlignment = velocityAlignment(movement, gravity, desiredDirection, base.getYaw(), base.getPitch());
        if (baseAlignment <= 0.0) {
            // 目标函数退化：交给 applyDegenerateFallback 的结果，不再沿用旧 pitch。
            return base;
        }
        double previousAlignment = velocityAlignment(movement, gravity, desiredDirection, base.getYaw(), previousPitch);
        if (previousAlignment < baseAlignment - PITCH_CONTINUITY_EPSILON) {
            return base;
        }

        ElytraDebug.log(ElytraDebug.SLOT_ROTATION, "rotation.keepPitch",
                "pitch=" + ElytraDebug.fmt(previousPitch)
                        + " base=" + ElytraDebug.fmt(base.getPitch())
                        + " align=" + ElytraDebug.fmt(previousAlignment)
                        + "/" + ElytraDebug.fmt(baseAlignment));
        return new Rot2f(base.getYaw(), previousPitch);
    }

    /**
     * 沿用上一次的逃逸旋转（若仍满足完整预演）。避免在多个等价安全解之间逐 tick 翻转。
     */
    private static Rot2f keepPreviousEscape(LocalPlayer player, boolean ceilingEscape) {
        Rot2f previous = lastEscapeRotation;
        if (previous == null) {
            return null;
        }
        float yaw = Mth.wrapDegrees(previous.getYaw());
        float pitch = applyCeilingEscape(Mth.clamp(previous.getPitch(), -89.0f, 89.0f), ceilingEscape);
        if (trajectorySafeTicks(player, yaw, pitch) < TRAJECTORY_HORIZON_TICKS) {
            return null;
        }
        return new Rot2f(yaw, pitch);
    }

    /**
     * 在固定候选集合里搜索通过完整预演的旋转。
     *
     * @param nearPrevious 为真时只考虑与上一次选择夹角不超过 {@code ESCAPE_KEEP_ANGLE} 的候选
     * @return 找到的旋转；没找到返回 null
     */
    private static Rot2f searchEscape(LocalPlayer player, Rot2f base, boolean ceilingEscape, boolean nearPrevious) {
        for (RotationOffset offset : ESCAPE_OFFSETS) {
            float yaw = Mth.wrapDegrees(base.getYaw() + offset.yawOffset());
            float pitch = applyCeilingEscape(
                    Mth.clamp(base.getPitch() + offset.pitchOffset(), -89.0f, 89.0f),
                    ceilingEscape
            );
            if (nearPrevious && angleToLastEscape(yaw, pitch) > ESCAPE_KEEP_ANGLE) {
                continue;
            }
            if (trajectorySafeTicks(player, yaw, pitch) >= TRAJECTORY_HORIZON_TICKS) {
                return new Rot2f(yaw, pitch);
            }
        }
        return null;
    }

    /**
     * 没有完整安全解时的兜底：取安全 tick 最多的候选。
     */
    private static Rot2f bestEffortEscape(LocalPlayer player, Rot2f base, boolean ceilingEscape) {
        float bestYaw = base.getYaw();
        float bestPitch = base.getPitch();
        int bestSafeTicks = -1;
        for (RotationOffset offset : ESCAPE_OFFSETS) {
            float yaw = Mth.wrapDegrees(base.getYaw() + offset.yawOffset());
            float pitch = applyCeilingEscape(
                    Mth.clamp(base.getPitch() + offset.pitchOffset(), -89.0f, 89.0f),
                    ceilingEscape
            );
            int safeTicks = trajectorySafeTicks(player, yaw, pitch);
            if (safeTicks > bestSafeTicks) {
                bestSafeTicks = safeTicks;
                bestYaw = yaw;
                bestPitch = pitch;
            }
        }
        return new Rot2f(bestYaw, bestPitch);
    }

    /**
     * 与上一次逃逸选择的加权角差；没有上一次选择时返回 0，让所有候选都有机会。
     */
    private static double angleToLastEscape(float yaw, float pitch) {
        Rot2f previous = lastEscapeRotation;
        if (previous == null) {
            return 0.0;
        }
        return Math.abs(Mth.wrapDegrees(yaw - previous.getYaw()))
                + 0.75 * Math.abs(pitch - previous.getPitch());
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
        // 比较的是"保持该旋转若干 tick 后滑翔方程输出的速度方向"，而不是实体当前 look。
        // 只预演 1 tick 时目标函数会强烈偏向"维持当前速度方向"，求解器宁可平飞也不压机头，
        // 表现为锁不住高度、绕着目标画圆；用多 tick 视野才能真正朝期望方向转向。
        Vec3 predicted = movement;
        for (int tick = 0; tick < ALIGNMENT_HORIZON_TICKS; tick++) {
            predicted = ElytraMotionPredictor.nextFallFlyingMovement(predicted, yaw, pitch, gravity);
        }
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
     *
     * <p>预演距离用「意图抬升量」而不是当前实际上升速度：后者会被抬头保护自身的低头结果反向影响，
     * 形成「拉升 → 探测变长 → 强制低头 → 上升变慢 → 探测变短 → 又拉升」的每 tick 振荡，
     * 表现就是上升时抬头/低头互相抢、转头抽风。</p>
     */
    private static boolean shouldAvoidCeilingLift(LocalPlayer player, double intendedClimb) {
        if (!player.isFallFlying()) {
            return false;
        }

        AABB box = player.getBoundingBox();
        double probeDistance = CEILING_PROBE_DISTANCE + Math.max(0.0, intendedClimb);
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

    public static double effectiveGravity(LocalPlayer player) {
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
