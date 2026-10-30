package com.github.epsilon.utils.player

import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.utils.NonNullContext

/**
 * 依据当前旋转与移动输入计算水平速度向量。
 *
 * `RotationManager.INSTANCE` 是可变静态字段（切换旋转模式会替换实例），
 * 因此每次调用都重新读取，不做缓存。
 */
object MoveUtils {

    /**
     * 根据当前旋转计算忽略横移输入的水平速度向量。
     *
     * @param speed 移动速度
     */
    fun forwardWithoutStrafe(speed: Double): DoubleArray {
        val yaw = RotationManager.INSTANCE.getYaw()

        val rad = Math.toRadians((yaw + 90.0f).toDouble())

        val d4 = speed * Math.cos(rad)
        val d5 = speed * Math.sin(rad)

        return doubleArrayOf(d4, d5)
    }

    /**
     * 根据当前旋转和移动输入计算水平速度向量。
     *
     * @param speed 移动速度
     */
    fun forward(speed: Double): DoubleArray {
        var yaw = RotationManager.INSTANCE.getYaw()
        val ctx = NonNullContext.current ?: return doubleArrayOf(0.0, 0.0)

        val moveVector = ctx.player.input.moveVector
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

        val sin = Math.sin(rad)
        val cos = Math.cos(rad)

        val d4 = forward * speed * cos + left * speed * sin
        val d5 = forward * speed * sin - left * speed * cos

        return doubleArrayOf(d4, d5)
    }

}
