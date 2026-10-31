package com.github.epsilon.modules.impl.combat.elytra_combat.flight

import com.github.epsilon.utils.math.toDegrees
import com.github.epsilon.utils.math.toRadians
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 主线程上的有界局部避障。
 *
 * 直线航段不可用时，只搜索有限的 yaw/pitch 偏移，不会在客户端线程运行完整 A*。
 */
object LocalFlightAvoidance {

    /**
     * 围绕期望方向采样的固定夹角集合；组合数量有界，适合主线程运行。
     */
    private val YAW_OFFSETS = floatArrayOf(-90.0f, -60.0f, -30.0f, 0.0f, 30.0f, 60.0f, 90.0f)
    private val PITCH_OFFSETS = floatArrayOf(-40.0f, -25.0f, -10.0f, 0.0f, 10.0f, 25.0f, 40.0f)

    fun findAvoidance(
        player: LocalPlayer,
        desiredVelocity: Vec3,
        targetPoint: Vec3,
        probeDistance: Double,
    ): Vec3? {
        return findAvoidance(player, desiredVelocity, targetPoint, probeDistance, null)
    }

    fun findAvoidance(
        player: LocalPlayer,
        desiredVelocity: Vec3,
        targetPoint: Vec3,
        probeDistance: Double,
        preferredDirection: Vec3?,
    ): Vec3? {
        if (desiredVelocity.lengthSqr() < 1.0E-8) {
            return null
        }

        var targetDirection = targetPoint.subtract(player.position())
        if (targetDirection.lengthSqr() < 1.0E-8) {
            targetDirection = desiredVelocity
        }
        targetDirection = targetDirection.normalize()

        val baseDirection = desiredVelocity.normalize()
        val baseYaw = Mth.wrapDegrees((atan2(baseDirection.z, baseDirection.x)).toDegrees().toFloat() - 90.0f)
        val basePitch =
            -(atan2(baseDirection.y, maxOf(0.001, baseDirection.horizontalDistance()))).toDegrees().toFloat()
        val preferred = if (preferredDirection == null || preferredDirection.lengthSqr() < 1.0E-8) {
            null
        } else {
            preferredDirection.normalize()
        }

        var best: Vec3? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (yawOffset in YAW_OFFSETS) {
            for (pitchOffset in PITCH_OFFSETS) {
                val yaw = Mth.wrapDegrees(baseYaw + yawOffset)
                val pitch = (basePitch + pitchOffset).coerceIn(-89.0f, 89.0f)
                val direction = direction(yaw, pitch)
                val candidate = player.position().add(direction.scale(probeDistance))
                if (!isSegmentClear(player, player.position(), candidate)) {
                    continue
                }

                val alignment = direction.dot(targetDirection)
                // continuity 让避障方向延续上一 tick 的选择，避免对称障碍前左右摇摆。
                val continuity = if (preferred == null) 0.0 else direction.dot(preferred)
                val score = alignment +
                        continuity * 0.75 -
                        abs(yawOffset) * 0.002 -
                        abs(pitchOffset) * 0.001
                if (score > bestScore) {
                    bestScore = score
                    best = direction.scale(desiredVelocity.length())
                }
            }
        }
        return best
    }

    fun isSegmentClear(player: LocalPlayer, from: Vec3, to: Vec3): Boolean {
        val delta = to.subtract(from)
        if (delta.lengthSqr() < 1.0E-8) {
            return true
        }

        // 使用完整玩家碰撞箱的扫掠体，与服务器移动碰撞语义一致。
        val playerBox = player.boundingBox.deflate(1.0E-4)
        val swept = playerBox.expandTowards(delta)
        if (!player.level().noBorderCollision(player, playerBox.move(delta))) {
            return false
        }
        for (shape in player.level().getBlockCollisions(player, swept)) {
            if (playerBox.collidedAlongVector(delta, shape.toAabbs())) {
                return false
            }
        }
        return true
    }

    private fun direction(yaw: Float, pitch: Float): Vec3 {
        val yawRad = (yaw.toDouble()).toRadians()
        val pitchRad = (pitch.toDouble()).toRadians()
        val horizontal = cos(pitchRad)
        return Vec3(
            -sin(yawRad) * horizontal,
            -sin(pitchRad),
            cos(yawRad) * horizontal,
        )
    }

}
