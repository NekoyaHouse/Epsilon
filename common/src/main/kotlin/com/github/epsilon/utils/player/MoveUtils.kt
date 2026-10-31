package com.github.epsilon.utils.player

import com.github.epsilon.managers.RotationManager
import com.github.epsilon.utils.math.toRadians
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.utils.safety.player
import kotlin.math.cos
import kotlin.math.sin

object MoveUtils {

    /**
     * 根据当前旋转计算忽略横移输入的水平速度向量。
     *
     * @param speed 移动速度
     */
    fun forwardWithoutStrafe(speed: Double): DoubleArray {
        val yaw = RotationManager.getYaw()

        val rad = ((yaw + 90.0f).toDouble()).toRadians()

        val d4 = speed * cos(rad)
        val d5 = speed * sin(rad)

        return doubleArrayOf(d4, d5)
    }

    /**
     * 根据当前旋转和移动输入计算水平速度向量。
     *
     * @param speed 移动速度
     */
    context(nn: NonNullContext)
    fun forward(speed: Double): DoubleArray {
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

        val rad = ((yaw + 90.0f).toDouble()).toRadians()

        val sin = sin(rad)
        val cos = cos(rad)

        val d4 = forward * speed * cos + left * speed * sin
        val d5 = forward * speed * sin - left * speed * cos

        return doubleArrayOf(d4, d5)
    }

}
