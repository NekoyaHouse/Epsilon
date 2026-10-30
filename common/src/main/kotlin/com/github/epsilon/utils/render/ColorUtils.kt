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
        val clampedFraction = Mth.clamp(fraction, 0.0f, 1.0f)

        val red = Mth.clamp(Mth.lerpInt(clampedFraction, color1.red, color2.red), 0, 255)
        val green = Mth.clamp(Mth.lerpInt(clampedFraction, color1.green, color2.green), 0, 255)
        val blue = Mth.clamp(Mth.lerpInt(clampedFraction, color1.blue, color2.blue), 0, 255)
        val alpha = Mth.clamp(Mth.lerpInt(clampedFraction, color1.alpha, color2.alpha), 0, 255)

        return Color(red, green, blue, alpha)
    }

}
