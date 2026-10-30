package com.github.epsilon.utils.math

import java.util.concurrent.ThreadLocalRandom

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
        return if (min >= max) min else ThreadLocalRandom.current().nextFloat(min, Math.nextUp(max))
    }

    /**
     * 生成给定闭区间内的随机值。
     *
     * @param min 闭区间最小值
     * @param max 闭区间最大值
     * @return 获取或计算得到的结果
     */
    fun getRandom(min: Double, max: Double): Double {
        return if (min >= max) min else ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max))
    }

}
