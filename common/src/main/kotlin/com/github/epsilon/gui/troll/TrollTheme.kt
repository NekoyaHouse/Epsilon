package com.github.epsilon.gui.troll

import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.scene.LayerOffset
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

    fun rowHeight(metrics: UiTextMetrics): Float {
        return metrics.textHeight(TEXT_SCALE) + 4.5f
    }

    fun headerHeight(metrics: UiTextMetrics): Float {
        return metrics.textHeight(TEXT_SCALE) + 9.0f
    }

    fun alpha(color: Color, alpha: Int): Color {
        return Color(
            color.red,
            color.green,
            color.blue,
            (alpha.toLong()).coerceIn(((0).toLong()).toLong(), ((255).toLong()).toLong()).toInt().toInt()
        )
    }

    fun UiScope.Window(headerHeight: Float, title: String) {
        Fill(GuiSetting.backgroundColor)
        if (GuiSetting.titleBar) {
            Rect(0f, 0f, width, headerHeight, primary())
        }
        Layer(TrollZ.Text) {
            TextWithShadow(trim(title, metrics, TEXT_SCALE, width - 9f), 4.5f, 4.5f, TEXT_SCALE, text())
        }
        if (GuiSetting.windowOutline) {
            Layer(TrollZ.Content) {
                RectOutline(0f, 0f, width, height, 0.5f, alpha(primary(), 255))
            }
        }
    }

    fun trim(value: String?, metrics: UiTextMetrics, scale: Float, width: Float): String {
        if (value == null || width <= 0.0f) return ""
        if (metrics.textWidth(value, scale) <= width) return value
        val suffix = "..."
        if (metrics.textWidth(suffix, scale) > width) return ""
        var end = value.length
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1)
            val result = value.substring(0, end) + suffix
            if (metrics.textWidth(result, scale) <= width) return result
        }
        return suffix
    }
}

/**
 * Troll GUI 的层叠语义。
 *
 * 历史实现里这些偏移是散落在各处的 1..5；这里把每个数字固定到一种绘制类别上，
 * **数值保持不变**，避免重构改变任何遮挡顺序。
 *
 * 由于历史代码是按「该元素当时需要压过谁」随手取值的，个别位置的语义并不直观：
 * - 部分窗口的**文字**位于第 2 层（[Highlight]），与悬停高亮同层；
 * - [TrollTheme.Window] 的描边位于第 4 层（[Content]），而设置窗口的卡片描边在第 5 层（[Overlay]）；
 * - 删除/移除按钮的底色位于第 3 层（[Text]）。
 *
 * 若后续要让命名与直觉一致，需要单独评估并承担遮挡顺序变化的风险，不应随本次重构一起改。
 */
object TrollZ {

    /** 1：行底色、进度条、面板底衬。 */
    val Base: LayerOffset = LayerOffset(1)

    /** 2：悬停高亮、按钮底、以及历史遗留在此层的部分文字。 */
    val Highlight: LayerOffset = LayerOffset(2)

    /** 3：文字、部分按钮底色。 */
    val Text: LayerOffset = LayerOffset(3)

    /** 4：光标、色块、以及 [TrollTheme.Window] 的描边。 */
    val Content: LayerOffset = LayerOffset(4)

    /** 5：外描边。 */
    val Overlay: LayerOffset = LayerOffset(5)

}
