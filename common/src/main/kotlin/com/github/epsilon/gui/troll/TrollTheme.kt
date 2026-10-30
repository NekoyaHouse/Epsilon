package com.github.epsilon.gui.troll

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.modules.impl.GuiSetting
import java.awt.Color

object TrollTheme {

    const val TEXT_SCALE = 0.975f
    const val VALUE_SCALE = 0.735f

    fun primary(): Color {
        return GuiSetting.primaryColor
    }

    fun text(): Color {
        return GuiSetting.textColor
    }

    fun xMargin(): Float {
        return GuiSetting.xMargin.toFloat()
    }

    fun yMargin(): Float {
        return GuiSetting.yMargin.toFloat()
    }

    fun rowHeight(metrics: TextRenderer): Float {
        return metrics.getHeight(TEXT_SCALE) + 4.5f
    }

    fun headerHeight(metrics: TextRenderer): Float {
        return metrics.getHeight(TEXT_SCALE) + 9.0f
    }

    fun alpha(color: Color, alpha: Int): Color {
        return Color(color.red, color.green, color.blue, Math.clamp(alpha.toLong(), 0, 255))
    }

    fun window(scope: UiTree.Scope, bounds: UiRect, headerHeight: Float, title: String, metrics: TextRenderer) {
        val titleBar = GuiSetting.titleBar
        val background = GuiSetting.backgroundColor
        scope.rect(bounds.x, bounds.y, bounds.width, bounds.height, background)
        if (titleBar) {
            scope.rect(bounds.x, bounds.y, bounds.width, headerHeight, primary())
        }
        scope.text(3, trim(title, metrics, TEXT_SCALE, bounds.width - 9.0f), bounds.x + 4.5f, bounds.y + 4.5f, TEXT_SCALE, text())
        if (GuiSetting.windowOutline) {
            scope.rectOutline(4, bounds.x, bounds.y, bounds.width, bounds.height, 0.5f, alpha(primary(), 255))
        }
    }

    fun trim(value: String?, metrics: TextRenderer, scale: Float, width: Float): String {
        if (value == null || width <= 0.0f) return ""
        if (metrics.getWidth(value, scale) <= width) return value
        val suffix = "..."
        if (metrics.getWidth(suffix, scale) > width) return ""
        var end = value.length
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1)
            val result = value.substring(0, end) + suffix
            if (metrics.getWidth(result, scale) <= width) return result
        }
        return suffix
    }

    fun metrics(renderer: TextRenderer): UiTextMetrics {
        return object : UiTextMetrics {
            override fun textWidth(text: String, scale: Float): Float {
                return renderer.getWidth(text, scale)
            }

            override fun textHeight(scale: Float): Float {
                return renderer.getHeight(scale)
            }

            override fun textWidth(text: String, scale: Float, fontLoader: TtfFontLoader): Float {
                return renderer.getWidth(text, scale, fontLoader)
            }

            override fun textHeight(scale: Float, fontLoader: TtfFontLoader): Float {
                return renderer.getHeight(scale, fontLoader)
            }
        }
    }

}
