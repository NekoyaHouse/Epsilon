package com.github.epsilon.utils.render

import net.minecraft.util.Mth
import java.awt.Color

object ColorUtils {

    /**
     * 在两种颜色之间进行线性插值。
     *
     * @param color1   第一种颜色
     * @param color2   第二种颜色
     * @param fraction 从第一种颜色到第二种颜色的插值比例
     * @return 操作结果
     */
    fun interpolateColor(color1: Color, color2: Color, fraction: Float): Color {
        val clampedFraction = fraction.coerceIn(0.0f, 1.0f)

        val red = Mth.lerpInt(clampedFraction, color1.red, color2.red).coerceIn(0, 255)
        val green = Mth.lerpInt(clampedFraction, color1.green, color2.green).coerceIn(0, 255)
        val blue = Mth.lerpInt(clampedFraction, color1.blue, color2.blue).coerceIn(0, 255)
        val alpha = Mth.lerpInt(clampedFraction, color1.alpha, color2.alpha).coerceIn(0, 255)

        return Color(red, green, blue, alpha)
    }

}
