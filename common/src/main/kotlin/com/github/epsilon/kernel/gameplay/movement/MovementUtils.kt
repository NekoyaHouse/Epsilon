package com.github.epsilon.kernel.gameplay.movement

import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.managers.RotationManager
import kotlin.math.cos
import kotlin.math.sin

object MovementUtils {

    /**
     * 根据当前旋转计算忽略横移输入的水平速度向量。
     *
     * @param speed 移动速度
     */
    context(nn: NonNullContext)
    fun forwardWithoutStrafe(speed: Double): DoubleArray {
        val yaw = RotationManager.getYaw()

        val rad = Math.toRadians((yaw + 90.0f).toDouble())

        val d4 = speed * cos(rad)
        val d5 = speed * sin(rad)

        return doubleArrayOf(d4, d5)
    }

    /**
     * 根据当前旋转和移动输入计算水平速度向量。
     *
     * @param speed 移动速度
     */
    fun NonNullContext.forward(speed: Double): DoubleArray {
        var yaw = RotationManager.getYaw()

        val moveVector = player.input.moveVector
        var forward = moveVector.y
        var left = moveVector.x

        if (forward != 0.0f) {
            if (left > 0.0f) {
                yaw += if (forward > 0.0f) -45f else 45f
            } else if (left < 0.0f) {
                yaw += if (forward > 0.0f) 45f else -45f
            }
            left = 0.0f
            if (forward > 0.0f) {
                forward = 1.0f
            } else if (forward < 0.0f) {
                forward = -1.0f
            }
        }

        val rad = Math.toRadians((yaw + 90.0f).toDouble())

        val sin = sin(rad)
        val cos = cos(rad)

        val d4 = forward * speed * cos + left * speed * sin
        val d5 = forward * speed * sin - left * speed * cos

        return doubleArrayOf(d4, d5)
    }

}
