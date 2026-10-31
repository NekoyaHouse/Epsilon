package com.github.epsilon.modules.impl.combat.elytra_combat.flight

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

/**
 * 26.3 滑翔运动方程工具。
 *
 * 基础 A* 只负责输出方块路径；这里保留 input 模式反解旋转和
 * [ElytraDirectionSolver] 安全预演所需的下一 tick 滑翔速度计算。
 */
object ElytraMotionPredictor {

    data class PlayerCollisionProfile(val width: Double, val height: Double)

    fun nextFallFlyingMovement(movement0: Vec3, yaw: Float, pitch: Float, gravity: Double): Vec3 {
        // 严格按 LivingEntity.updateFallFlyingMovement 的顺序复算：重力、转向修正、阻力。
        var movement = movement0
        val lookAngle = calculateViewVector(pitch, yaw)
        val leanAngle = pitch * (PI / 180.0)
        val lookHorizontalLength = sqrt(lookAngle.x * lookAngle.x + lookAngle.z * lookAngle.z)
        val movementHorizontalLength = movement.horizontalDistance()
        val liftForce = cos(leanAngle) * cos(leanAngle)

        movement = movement.add(0.0, gravity * (-1.0 + liftForce * 0.75), 0.0)
        if (movement.y < 0.0 && lookHorizontalLength > 0.0) {
            val convert = movement.y * -0.1 * liftForce
            movement = movement.add(
                lookAngle.x * convert / lookHorizontalLength,
                convert,
                lookAngle.z * convert / lookHorizontalLength,
            )
        }

        if (leanAngle < 0.0 && lookHorizontalLength > 0.0) {
            val convert = movementHorizontalLength * -sin(leanAngle) * 0.04
            movement = movement.add(
                -lookAngle.x * convert / lookHorizontalLength,
                convert * 3.2,
                -lookAngle.z * convert / lookHorizontalLength,
            )
        }

        if (lookHorizontalLength > 0.0) {
            movement = movement.add(
                (lookAngle.x / lookHorizontalLength * movementHorizontalLength - movement.x) * 0.1,
                0.0,
                (lookAngle.z / lookHorizontalLength * movementHorizontalLength - movement.z) * 0.1,
            )
        }

        return movement.multiply(0.99, 0.98, 0.99)
    }

    private fun calculateViewVector(pitch: Float, yaw: Float): Vec3 {
        val realPitch = pitch * (PI / 180.0).toFloat()
        val realYaw = -yaw * (PI / 180.0).toFloat()
        val yawCos = Mth.cos(realYaw.toDouble())
        val yawSin = Mth.sin(realYaw.toDouble())
        val pitchCos = Mth.cos(realPitch.toDouble())
        val pitchSin = Mth.sin(realPitch.toDouble())
        return Vec3((yawSin * pitchCos).toDouble(), (-pitchSin).toDouble(), (yawCos * pitchCos).toDouble())
    }

}
