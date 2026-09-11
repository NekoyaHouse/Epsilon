package com.github.epsilon.elements.impl.island;

import net.minecraft.util.Mth;

import java.awt.*;

public class IslandPalette {

    public static final Color TEXT_PRIMARY = new Color(246, 248, 252, 245);
    public static final Color TEXT_SECONDARY = new Color(207, 216, 230, 220);
    public static final Color TEXT_MUTED = new Color(153, 166, 184, 190);

    public static final Color ACCENT = new Color(111, 224, 211);
    public static final Color ACCENT_ALT = new Color(157, 166, 255);
    public static final Color SUCCESS = new Color(111, 224, 153);
    public static final Color WARNING = new Color(244, 201, 105);
    public static final Color DANGER = new Color(245, 125, 142);

    public static final Color TRACK = new Color(255, 255, 255, 48);
    public static final Color OUTLINE = new Color(255, 255, 255, 48);

    private IslandPalette() {
    }

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Mth.clamp(alpha, 0, 255));
    }

    public static Color withAlphaMul(Color color, float alpha) {
        return withAlpha(color, (int) (color.getAlpha() * Mth.clamp(alpha, 0.0f, 1.0f)));
    }

}
