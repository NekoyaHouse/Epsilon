package com.github.epsilon.elements.impl.island

import net.minecraft.util.Mth
import java.awt.Color

object IslandPalette {

    val TEXT_PRIMARY: Color = Color(246, 248, 252, 245)
    val TEXT_SECONDARY: Color = Color(207, 216, 230, 220)
    val TEXT_MUTED: Color = Color(153, 166, 184, 190)

    val ACCENT: Color = Color(111, 224, 211)
    val ACCENT_ALT: Color = Color(157, 166, 255)
    val SUCCESS: Color = Color(111, 224, 153)
    val WARNING: Color = Color(244, 201, 105)
    val DANGER: Color = Color(245, 125, 142)

    val TRACK: Color = Color(255, 255, 255, 48)
    val OUTLINE: Color = Color(255, 255, 255, 48)

    fun withAlpha(color: Color, alpha: Int): Color {
        return Color(color.red, color.green, color.blue, Mth.clamp(alpha, 0, 255))
    }

    fun withAlphaMul(color: Color, alpha: Float): Color {
        return withAlpha(color, (color.alpha * Mth.clamp(alpha, 0.0f, 1.0f)).toInt())
    }

}
