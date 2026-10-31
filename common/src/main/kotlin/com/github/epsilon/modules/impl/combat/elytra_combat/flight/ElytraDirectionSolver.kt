package com.github.epsilon.modules.impl.combat.elytra_combat.flight

import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraDirectionSolver.ESCAPE_KEEP_ANGLE
import com.github.epsilon.utils.math.toDegrees
import com.github.epsilon.utils.rotation.Rot2f
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2

/**
 * 将期望速度反解为 input 模式可用的 yaw/pitch。
 *
 * 因为滑翔速度受惯性、重力和阻力影响，直接看向目标并不等于实际飞向目标。
 * 这里固定 yaw 为期望水平方向，在 pitch 范围内采样并细化，使下一 tick 模拟速度与期望速度夹角最小。
 */
object ElytraDirectionSolver {

    /**
     * 安全解至少预演的 tick 数；短于该值的候选会被视为存在近期碰撞风险。
     */
    private const val TRAJECTORY_HORIZON_TICKS = 4

    /**
     * 抬头保护探测距离与逃逸 pitch；与 ControlElytraFlightMode 保持一致。
     */
    private const val CEILING_PROBE_DISTANCE = 0.75
    private const val CEILING_PROBE_EPSILON = 1.0E-4
    private const val CEILING_ESCAPE_PITCH = 5.0f

    private val ESCAPE_OFFSETS: List<RotationOffset> = createEscapeOffsets()

    /**
     * 重新搜索逃逸解时，优先在与上一次选择夹角不超过该值的候选里挑，保持机动连续。
     */
    private const val ESCAPE_KEEP_ANGLE = 30.0

    /**
     * 上次安全逃逸旋转；仍安全时复用，避免等价解之间切换。
     */
    private var lastEscapeRotation: Rot2f? = null

    /**
     * 上次提交的俯仰角，用于目标函数近似等价时保持连续性。
     */
    private var lastSolvedPitch: Float = Float.NaN

    /**
     * 俯仰连续性判断的贴合度容差。
     */
    private const val PITCH_CONTINUITY_EPSILON = 0.05

    /**
     * 求解俯仰相对意图俯仰的默认最大偏差（度）。
     */
    const val DEFAULT_PITCH_DEVIATION_LIMIT: Float = 35.0f

    /**
     * 精确瞄准容差上限；不超过此值时跳过旧俯仰复用。
     */
    private const val PRECISE_PITCH_TOLERANCE = 5.0f

    /**
     * 速度对齐的预演 tick 数，与安全预演视野一致。
     */
    private const val ALIGNMENT_HORIZON_TICKS = 4

    /**
     * 清空逃逸与俯仰记忆；模块状态重置或接管结束时调用，避免沿用上一段飞行的选择。
     */
    fun resetEscape() {
        lastEscapeRotation = null
        lastSolvedPitch = Float.NaN
    }

    /**
     * 记录本 tick 实际提交的俯仰角，供下一 tick 的连续性判断使用（只应由主线程调用）。
     */
    fun rememberPitch(pitch: Float) {
        lastSolvedPitch = pitch
    }

    /**
     * 主线程使用的安全解。
     *
     * 先按速度对齐求出基础旋转，再用完整玩家碰撞箱沿 26.3 滑翔方程预演后续 tick。
     * 只要预演会碰到方块（包括头顶），就在基础解附近寻找差值最小、仍能通过完整预演的旋转，
     * 避免“下体过去但头顶撞方块”。
     */
    fun solveSafe(player: LocalPlayer, desiredVelocity: Vec3): Rot2f {
        return solveSafe(player, desiredVelocity, DEFAULT_PITCH_DEVIATION_LIMIT)
    }

    /**
     * 在主线程求解安全旋转。
     *
     * @param pitchDeviationLimit 俯仰允许偏离意图的最大角度（度）；精确瞄准应使用较小值
     */
    fun solveSafe(player: LocalPlayer, desiredVelocity: Vec3, pitchDeviationLimit: Float): Rot2f {
        if (desiredVelocity.lengthSqr() < 1.0E-8) {
            return Rot2f(player.yRot, player.xRot)
        }

        val ceilingEscape = shouldAvoidCeilingLift(player, desiredVelocity.y)
        var base = applyCeilingEscape(solve(player, desiredVelocity), ceilingEscape)
        base = applyDegenerateFallback(player, base, desiredVelocity, ceilingEscape)
        base = applyIntentPitchLimit(base, desiredVelocity, ceilingEscape, pitchDeviationLimit)
        if (pitchDeviationLimit > PRECISE_PITCH_TOLERANCE) {
            base = applyPitchContinuity(player, base, desiredVelocity, ceilingEscape, pitchDeviationLimit)
        }
        val baseSafeTicks = trajectorySafeTicks(player, base.yaw, base.pitch)
        ElytraDebug.log(
            ElytraDebug.SLOT_ROTATION,
            "rotation.solve",
            "ceiling=" + ceilingEscape +
                    " pitch=" + ElytraDebug.fmt(base.pitch.toDouble()) +
                    " yaw=" + ElytraDebug.fmt(base.yaw.toDouble()) +
                    " safeTicks=" + baseSafeTicks,
        )
        if (baseSafeTicks >= TRAJECTORY_HORIZON_TICKS) {
            lastEscapeRotation = null
            return base
        }

        val kept = keepPreviousEscape(player, ceilingEscape)
        if (kept != null) {
            ElytraDebug.log(
                ElytraDebug.SLOT_ROTATION,
                "rotation.escape",
                "keep pitch=" + ElytraDebug.fmt(kept.pitch.toDouble()) + " yaw=" + ElytraDebug.fmt(kept.yaw.toDouble()),
            )
            return kept
        }

        if (lastEscapeRotation != null) {
            val near = searchEscape(player, base, ceilingEscape, true)
            if (near != null) {
                lastEscapeRotation = near
                ElytraDebug.log(
                    ElytraDebug.SLOT_ROTATION,
                    "rotation.escape",
                    "near pitch=" + ElytraDebug.fmt(near.pitch.toDouble()) + " yaw=" + ElytraDebug.fmt(near.yaw.toDouble()),
                )
                return near
            }
        }

        val picked = searchEscape(player, base, ceilingEscape, false)
        if (picked != null) {
            lastEscapeRotation = picked
            ElytraDebug.log(
                ElytraDebug.SLOT_ROTATION,
                "rotation.escape",
                "pick pitch=" + ElytraDebug.fmt(picked.pitch.toDouble()) + " yaw=" + ElytraDebug.fmt(picked.yaw.toDouble()),
            )
            return picked
        }

        val fallback = bestEffortEscape(player, base, ceilingEscape)
        lastEscapeRotation = fallback
        return fallback
    }

    /**
     * 将求解俯仰限制在意图俯仰的允许偏差内。
     */
    private fun applyIntentPitchLimit(
        base: Rot2f,
        desiredVelocity: Vec3,
        ceilingEscape: Boolean,
        pitchDeviationLimit: Float,
    ): Rot2f {
        val desiredPitch = intentPitch(desiredVelocity, ceilingEscape)
        val limited = Mth.clamp(
            base.pitch,
            desiredPitch - pitchDeviationLimit,
            desiredPitch + pitchDeviationLimit,
        )
        if (limited == base.pitch) {
            return base
        }
        ElytraDebug.log(
            ElytraDebug.SLOT_ROTATION,
            "rotation.pitchLimit",
            "base=" + ElytraDebug.fmt(base.pitch.toDouble()) +
                    " desired=" + ElytraDebug.fmt(desiredPitch.toDouble()) +
                    " ->" + ElytraDebug.fmt(limited.toDouble()),
        )
        return Rot2f(base.yaw, limited)
    }

    /**
     * 意图方向本身的俯仰角（已按抬头保护上调）。
     */
    private fun intentPitch(desiredVelocity: Vec3, ceilingEscape: Boolean): Float {
        return applyCeilingEscape(pitchOf(desiredVelocity.normalize()), ceilingEscape)
    }

    /**
     * 速度对齐结果非正时采用意图俯仰，避免沿用反向姿态。
     */
    private fun applyDegenerateFallback(
        player: LocalPlayer,
        base: Rot2f,
        desiredVelocity: Vec3,
        ceilingEscape: Boolean,
    ): Rot2f {
        val desiredDirection = desiredVelocity.normalize()
        val alignment = velocityAlignment(
            player.deltaMovement,
            effectiveGravity(player),
            desiredDirection,
            base.yaw,
            base.pitch,
        )
        if (alignment > 0.0) {
            return base
        }

        val desiredPitch = applyCeilingEscape(pitchOf(desiredDirection), ceilingEscape)
        ElytraDebug.log(
            ElytraDebug.SLOT_ROTATION,
            "rotation.degenerate",
            "align=" + ElytraDebug.fmt(alignment) +
                    " base=" + ElytraDebug.fmt(base.pitch.toDouble()) +
                    " -> desired=" + ElytraDebug.fmt(desiredPitch.toDouble()),
        )
        return Rot2f(base.yaw, desiredPitch)
    }

    /**
     * 俯仰连续性：目标函数出现平台时（当前速度与期望方向相反最容易出现），
     * 只要上一 tick 的 pitch 与最优解贴合度差距在容差内，就沿用它，避免 pitch 逐 tick 乱跳。
     */
    private fun applyPitchContinuity(
        player: LocalPlayer,
        base: Rot2f,
        desiredVelocity: Vec3,
        ceilingEscape: Boolean,
        pitchDeviationLimit: Float,
    ): Rot2f {
        if (lastSolvedPitch.isNaN()) {
            return base
        }

        var previousPitch = applyCeilingEscape(lastSolvedPitch, ceilingEscape)
        // 上一 tick 的姿态也要落在本 tick 的意图带内，避免意图已反向时继续沿用旧姿态。
        val bandPitch = intentPitch(desiredVelocity, ceilingEscape)
        previousPitch = Mth.clamp(
            previousPitch,
            bandPitch - pitchDeviationLimit,
            bandPitch + pitchDeviationLimit,
        )
        val movement = player.deltaMovement
        val gravity = effectiveGravity(player)
        val desiredDirection = desiredVelocity.normalize()
        val baseAlignment = velocityAlignment(movement, gravity, desiredDirection, base.yaw, base.pitch)
        if (baseAlignment <= 0.0) {
            return base
        }
        val previousAlignment = velocityAlignment(movement, gravity, desiredDirection, base.yaw, previousPitch)
        if (previousAlignment < baseAlignment - PITCH_CONTINUITY_EPSILON) {
            return base
        }

        ElytraDebug.log(
            ElytraDebug.SLOT_ROTATION,
            "rotation.keepPitch",
            "pitch=" + ElytraDebug.fmt(previousPitch.toDouble()) +
                    " base=" + ElytraDebug.fmt(base.pitch.toDouble()) +
                    " align=" + ElytraDebug.fmt(previousAlignment) +
                    "/" + ElytraDebug.fmt(baseAlignment),
        )
        return Rot2f(base.yaw, previousPitch)
    }

    /**
     * 沿用上一次的逃逸旋转（若仍满足完整预演）。避免在多个等价安全解之间逐 tick 翻转。
     */
    private fun keepPreviousEscape(player: LocalPlayer, ceilingEscape: Boolean): Rot2f? {
        val previous = lastEscapeRotation ?: return null
        val yaw = Mth.wrapDegrees(previous.yaw)
        val pitch = applyCeilingEscape(Mth.clamp(previous.pitch, -89.0f, 89.0f), ceilingEscape)
        if (trajectorySafeTicks(player, yaw, pitch) < TRAJECTORY_HORIZON_TICKS) {
            return null
        }
        return Rot2f(yaw, pitch)
    }

    /**
     * 在固定候选集合里搜索通过完整预演的旋转。
     *
     * @param nearPrevious 为真时只考虑与上一次选择夹角不超过 [ESCAPE_KEEP_ANGLE] 的候选
     * @return 找到的旋转；没找到返回 null
     */
    private fun searchEscape(
        player: LocalPlayer,
        base: Rot2f,
        ceilingEscape: Boolean,
        nearPrevious: Boolean,
    ): Rot2f? {
        for (offset in ESCAPE_OFFSETS) {
            val yaw = Mth.wrapDegrees(base.yaw + offset.yawOffset)
            val pitch = applyCeilingEscape(
                Mth.clamp(base.pitch + offset.pitchOffset, -89.0f, 89.0f),
                ceilingEscape,
            )
            if (nearPrevious && angleToLastEscape(yaw, pitch) > ESCAPE_KEEP_ANGLE) {
                continue
            }
            if (trajectorySafeTicks(player, yaw, pitch) >= TRAJECTORY_HORIZON_TICKS) {
                return Rot2f(yaw, pitch)
            }
        }
        return null
    }

    /**
     * 没有完整安全解时的兜底：取安全 tick 最多的候选。
     */
    private fun bestEffortEscape(player: LocalPlayer, base: Rot2f, ceilingEscape: Boolean): Rot2f {
        var bestYaw = base.yaw
        var bestPitch = base.pitch
        var bestSafeTicks = -1
        for (offset in ESCAPE_OFFSETS) {
            val yaw = Mth.wrapDegrees(base.yaw + offset.yawOffset)
            val pitch = applyCeilingEscape(
                Mth.clamp(base.pitch + offset.pitchOffset, -89.0f, 89.0f),
                ceilingEscape,
            )
            val safeTicks = trajectorySafeTicks(player, yaw, pitch)
            if (safeTicks > bestSafeTicks) {
                bestSafeTicks = safeTicks
                bestYaw = yaw
                bestPitch = pitch
            }
        }
        return Rot2f(bestYaw, bestPitch)
    }

    /**
     * 与上一次逃逸选择的加权角差；没有上一次选择时返回 0，让所有候选都有机会。
     */
    private fun angleToLastEscape(yaw: Float, pitch: Float): Double {
        val previous = lastEscapeRotation ?: return 0.0
        return abs(Mth.wrapDegrees(yaw - previous.yaw).toDouble()) +
                0.75 * abs((pitch - previous.pitch).toDouble())
    }

    /**
     * 与 input 模式真实执行一致的纯数学解，工作线程的路径预演也调用这一版本。
     */
    fun solve(player: LocalPlayer, desiredVelocity: Vec3): Rot2f {
        if (desiredVelocity.lengthSqr() < 1.0E-8) {
            return Rot2f(player.yRot, player.xRot)
        }
        return solve(player.deltaMovement, effectiveGravity(player), desiredVelocity)
    }

    fun solve(movement: Vec3, gravity: Double, desiredVelocity: Vec3): Rot2f {
        if (desiredVelocity.lengthSqr() < 1.0E-8) {
            return Rot2f(0.0f, 0.0f)
        }

        // yaw 直接取期望方向；pitch 先 5 度粗采样，再在最优区间二分细化。
        val desiredDirection = desiredVelocity.normalize()
        var yaw = (atan2(desiredDirection.z, desiredDirection.x)).toDegrees().toFloat() - 90.0f
        yaw = Mth.wrapDegrees(yaw)

        var bestPitch = Mth.clamp(pitchOf(desiredDirection), -89.0f, 89.0f)
        var bestDot = Double.NEGATIVE_INFINITY
        var pitch = -80.0f
        while (pitch <= 80.0f) {
            val dot = velocityAlignment(movement, gravity, desiredDirection, yaw, pitch)
            if (dot > bestDot) {
                bestDot = dot
                bestPitch = pitch
            }
            pitch += 5.0f
        }

        var low = maxOf(-89.0f, bestPitch - 5.0f)
        var high = minOf(89.0f, bestPitch + 5.0f)
        for (i in 0 until 20) {
            val mid = (low + high) * 0.5f
            val left = velocityAlignment(movement, gravity, desiredDirection, yaw, mid - 0.001f)
            val right = velocityAlignment(movement, gravity, desiredDirection, yaw, mid + 0.001f)
            if (left > right) {
                high = mid
            } else {
                low = mid
            }
        }
        return Rot2f(yaw, Mth.clamp((low + high) * 0.5f, -89.0f, 89.0f))
    }

    private fun velocityAlignment(
        movement: Vec3,
        gravity: Double,
        desiredDirection: Vec3,
        yaw: Float,
        pitch: Float,
    ): Double {
        // 按多 tick 滑翔结果评估方向，避免单 tick 目标函数过度偏向当前速度。
        var predicted = movement
        for (tick in 0 until ALIGNMENT_HORIZON_TICKS) {
            predicted = ElytraMotionPredictor.nextFallFlyingMovement(predicted, yaw, pitch, gravity)
        }
        if (predicted.lengthSqr() < 1.0E-8) {
            return Double.NEGATIVE_INFINITY
        }
        return predicted.normalize().dot(desiredDirection)
    }

    private fun trajectorySafeTicks(player: LocalPlayer, yaw: Float, pitch: Float): Int {
        // 用完整玩家 AABB 逐步推进滑翔方程；返回首次碰撞前的安全 tick 数。
        var position = player.position()
        var velocity = player.deltaMovement
        val gravity = effectiveGravity(player)
        for (tick in 0 until TRAJECTORY_HORIZON_TICKS) {
            val nextVelocity = ElytraMotionPredictor.nextFallFlyingMovement(velocity, yaw, pitch, gravity)
            val nextPosition = position.add(nextVelocity)
            if (!LocalFlightAvoidance.isSegmentClear(player, position, nextPosition)) {
                return tick
            }
            position = nextPosition
            velocity = nextVelocity
        }
        return TRAJECTORY_HORIZON_TICKS
    }

    /**
     * 使用意图抬升量探测头顶碰撞，避免实际升速与俯仰修正形成反馈振荡。
     */
    private fun shouldAvoidCeilingLift(player: LocalPlayer, intendedClimb: Double): Boolean {
        if (!player.isFallFlying) {
            return false
        }

        val box = player.boundingBox
        val probeDistance = CEILING_PROBE_DISTANCE + maxOf(0.0, intendedClimb)
        val ceilingProbe = AABB(
            box.minX + CEILING_PROBE_EPSILON,
            box.maxY - CEILING_PROBE_EPSILON,
            box.minZ + CEILING_PROBE_EPSILON,
            box.maxX - CEILING_PROBE_EPSILON,
            box.maxY + probeDistance,
            box.maxZ - CEILING_PROBE_EPSILON,
        )
        return !player.level().noBlockCollision(player, ceilingProbe)
    }

    private fun applyCeilingEscape(rotation: Rot2f, ceilingEscape: Boolean): Rot2f {
        if (!ceilingEscape || rotation.pitch >= CEILING_ESCAPE_PITCH) {
            return rotation
        }
        return Rot2f(rotation.yaw, CEILING_ESCAPE_PITCH)
    }

    private fun applyCeilingEscape(pitch: Float, ceilingEscape: Boolean): Float {
        return if (ceilingEscape) maxOf(pitch, CEILING_ESCAPE_PITCH) else pitch
    }

    private fun pitchOf(direction: Vec3): Float {
        val horizontal = maxOf(0.001, direction.horizontalDistance())
        return -(atan2(direction.y, horizontal)).toDegrees().toFloat()
    }

    fun effectiveGravity(player: LocalPlayer): Double {
        // 与原版 LivingEntity.getEffectiveGravity 一致：下落且缓降时重力和 0.01 取小。
        if (player.deltaMovement.y <= 0.0 && player.hasEffect(MobEffects.SLOW_FALLING)) {
            return minOf(player.gravity, 0.01)
        }
        return player.gravity
    }

    private fun createEscapeOffsets(): List<RotationOffset> {
        // 候选按偏移代价排序：优先尝试最小偏航/俯仰修正，实在不行再大幅转向。
        val yawOffsets = floatArrayOf(0.0f, 20.0f, -20.0f, 40.0f, -40.0f, 65.0f, -65.0f, 90.0f, -90.0f)
        val pitchOffsets = floatArrayOf(0.0f, 15.0f, -15.0f, 30.0f, -30.0f, 50.0f, -50.0f, 75.0f, -75.0f)
        val offsets = ArrayList<RotationOffset>(yawOffsets.size * pitchOffsets.size - 1)
        for (yaw in yawOffsets) {
            for (pitch in pitchOffsets) {
                if (yaw == 0.0f && pitch == 0.0f) {
                    continue
                }
                offsets.add(RotationOffset(yaw, pitch, (abs(yaw) + abs(pitch) * 0.75f).toDouble()))
            }
        }
        offsets.sortWith(Comparator.comparingDouble<RotationOffset> { offset -> offset.penalty })
        return offsets.toList()
    }

    private data class RotationOffset(val yawOffset: Float, val pitchOffset: Float, val penalty: Double)

}
