package com.github.epsilon.utils.math

import java.util.concurrent.ThreadLocalRandom
import kotlin.math.PI
import kotlin.math.nextUp

/**
 * `Math.toRadians` 的 Kotlin 版本：角度转弧度。
 */
fun Double.toRadians(): Double = this / 180.0 * PI

/**
 * `Math.toDegrees` 的 Kotlin 版本：弧度转角度。
 */
fun Double.toDegrees(): Double = this * 180.0 / PI

object MathUtils {

    /**
     * 生成给定闭区间内的随机值。
     *
     * @param min 闭区间最小值
     * @param max 闭区间最大值
     * @return 获取或计算得到的结果
     */
    fun getRandom(min: Int, max: Int): Int {
        return if (min >= max) min else ThreadLocalRandom.current().nextLong(min.toLong(), max.toLong() + 1L).toInt()
    }

    /**
     * 生成给定闭区间内的随机值。
     *
     * @param min 闭区间最小值
     * @param max 闭区间最大值
     * @return 获取或计算得到的结果
     */
    fun getRandom(min: Float, max: Float): Float {
        return if (min >= max) min else ThreadLocalRandom.current().nextFloat(min, (max).nextUp())
    }

    /**
     * 生成给定闭区间内的随机值。
     *
     * @param min 闭区间最小值
     * @param max 闭区间最大值
     * @return 获取或计算得到的结果
     */
    fun getRandom(min: Double, max: Double): Double {
        return if (min >= max) min else ThreadLocalRandom.current().nextDouble(min, (max).nextUp())
    }

}
