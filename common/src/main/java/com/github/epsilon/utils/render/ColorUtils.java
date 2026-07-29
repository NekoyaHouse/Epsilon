package com.github.epsilon.utils.render;

import net.minecraft.util.Mth;

import java.awt.*;

public class ColorUtils {

    /**
     * 在两种颜色之间进行线性插值。
     *
     * @param color1   第一种颜色
     * @param color2   第二种颜色
     * @param fraction 从第一种颜色到第二种颜色的插值比例
     * @return 操作结果
     */
    public static Color interpolateColor(Color color1, Color color2, float fraction) {
        fraction = Mth.clamp(fraction, 0.0f, 1.0f);

        int red = Mth.clamp(Mth.lerpInt(fraction, color1.getRed(), color2.getRed()), 0, 255);
        int green = Mth.clamp(Mth.lerpInt(fraction, color1.getGreen(), color2.getGreen()), 0, 255);
        int blue = Mth.clamp(Mth.lerpInt(fraction, color1.getBlue(), color2.getBlue()), 0, 255);
        int alpha = Mth.clamp(Mth.lerpInt(fraction, color1.getAlpha(), color2.getAlpha()), 0, 255);

        return new Color(red, green, blue, alpha);
    }

}
