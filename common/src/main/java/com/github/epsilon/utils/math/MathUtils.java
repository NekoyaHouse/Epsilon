package com.github.epsilon.utils.math;

import me.sofurry.ClInitNative;

import java.util.concurrent.ThreadLocalRandom;

@ClInitNative
public class MathUtils {

    private MathUtils() {
    }

    /**
     * 生成给定闭区间内的随机值。
     *
     * @param min 闭区间最小值
     * @param max 闭区间最大值
     * @return 获取或计算得到的结果
     */
    public static int getRandom(int min, int max) {
        return min >= max ? min : (int) ThreadLocalRandom.current().nextLong(min, (long) max + 1L);
    }

    /**
     * 生成给定闭区间内的随机值。
     *
     * @param min 闭区间最小值
     * @param max 闭区间最大值
     * @return 获取或计算得到的结果
     */
    public static float getRandom(float min, float max) {
        return min >= max ? min : ThreadLocalRandom.current().nextFloat(min, Math.nextUp(max));
    }

    /**
     * 生成给定闭区间内的随机值。
     *
     * @param min 闭区间最小值
     * @param max 闭区间最大值
     * @return 获取或计算得到的结果
     */
    public static double getRandom(double min, double max) {
        return min >= max ? min : ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max));
    }

}
