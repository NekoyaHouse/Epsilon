package com.github.epsilon.utils.math;

import java.util.concurrent.ThreadLocalRandom;

public class MathUtils {

    private MathUtils() {
    }

    // 返回 [min, max] 的闭区间随机整数
    public static int getRandom(int min, int max) {
        return min >= max ? min : (int) ThreadLocalRandom.current().nextLong(min, (long) max + 1L);
    }

    // 返回 [min, max] 的闭区间随机浮点数
    public static float getRandom(float min, float max) {
        return min >= max ? min : ThreadLocalRandom.current().nextFloat(min, Math.nextUp(max));
    }

    // 返回 [min, max] 的闭区间随机双精度数
    public static double getRandom(double min, double max) {
        return min >= max ? min : ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max));
    }

}
