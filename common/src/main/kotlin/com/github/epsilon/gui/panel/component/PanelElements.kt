package com.github.epsilon.gui.panel.component

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.theme.MD3Theme
import java.awt.Color

/**
 * 面板通用组件辅助方法集合。
 *
 * 该类统一提供行内控件的对齐规则，以及 switch、filled field、chip、segmented、icon button
 * 等语义元素的绘制/建树辅助，便于不同设置行保持一致的视觉和布局语义。
 */
object PanelElements {

    const val ROW_LABEL_INSET: Float = MD3Theme.ROW_CONTENT_INSET + 4.0f

    const val ICON_BUTTON_SIZE: Float = 20.0f

    fun buildRowSurface(scope: UiTree.Scope, bounds: UiRect, hoverProgress: Float) {
        scope.roundRect(
            0.0f, 0.0f, bounds.width, bounds.height,
            MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.rowSurface(hoverProgress)
        )
    }

    /**
     * 计算一行内容中主标签的起始 X 坐标。
     */
    fun rowLabelX(bounds: UiRect): Float = bounds.x + ROW_LABEL_INSET

    /**
     * 将一个控件对齐到行尾区域，并在垂直方向居中。
     */
    fun alignTrailing(bounds: UiRect, width: Float, height: Float): UiRect = UiRect(
        bounds.right() - MD3Theme.ROW_TRAILING_INSET - width,
        bounds.y + (bounds.height - height) / 2.0f,
        width,
        height
    )

    /**
     * 计算行内开关控件的标准区域。
     */
    fun switchBounds(bounds: UiRect): UiRect =
        alignTrailing(bounds, MD3Theme.SWITCH_WIDTH, MD3Theme.SWITCH_HEIGHT)

    fun compactFieldBounds(bounds: UiRect, width: Float): UiRect =
        alignTrailing(bounds, width, MD3Theme.CONTROL_HEIGHT)

    fun buildSwitch(scope: UiTree.Scope, rect: UiRect, toggleProgress: Float, hoverProgress: Float) {
        scope.toggle(rect, toggleProgress, hoverProgress)
    }

    /**
     * 在 DSL 中构建一个标准 filled field，并返回与其配套的语义色值。
     */
    fun buildFilledField(
        scope: UiTree.Scope, bounds: UiRect, focused: Boolean, hoverProgress: Float,
    ): FilledFieldColors {
        val text = MD3Theme.filledFieldContent(focused)
        val caret = MD3Theme.filledFieldCaret(focused)
        val indicator = MD3Theme.filledFieldIndicator(focused, hoverProgress)

        scope.input(
            bounds, focused, hoverProgress,
            6.0f, null, 0.0f, Color(0, 0, 0, 0),
            null, null,
            null, 0.0f, null
        )
        return FilledFieldColors(text, caret, indicator)
    }

    fun measureAssistChipBounds(
        textRenderer: TextRenderer, rowBounds: UiRect, label: String,
        textScale: Float, horizontalPadding: Float, trailingSlotWidth: Float, maxWidth: Float,
    ): UiRect {
        val desiredWidth = textRenderer.getWidth(label, textScale) + horizontalPadding * 2.0f + trailingSlotWidth
        return alignTrailing(rowBounds, Math.min(maxWidth, desiredWidth), MD3Theme.COMPACT_CHIP_HEIGHT)
    }

    /**
     * 在 DSL 中构建一个 assist chip 语义节点。
     */
    fun buildAssistChip(
        scope: UiTree.Scope, textRenderer: TextRenderer, bounds: UiRect,
        label: String, textScale: Float, background: Color, foreground: Color,
        trailingIcon: String?, trailingIconScale: Float, trailingIconFont: TtfFontLoader?,
    ) {
        scope.chip(bounds, label, textScale, background, foreground, trailingIcon, trailingIconScale, trailingIconFont)
    }

    /**
     * 在 DSL 中构建一个双段 segmented control 节点。
     */
    fun buildSegmentedControl(
        scope: UiTree.Scope, textRenderer: TextRenderer,
        bounds: UiRect, leadingLabel: String, trailingLabel: String,
        progress: Float, hoverProgress: Float,
    ) {
        scope.segmented(bounds, leadingLabel, trailingLabel, progress, hoverProgress)
    }

    /**
     * 在 DSL 中构建一个圆角图标按钮节点。
     */
    fun buildIconButton(
        scope: UiTree.Scope, textRenderer: TextRenderer, bounds: UiRect,
        label: String, scale: Float, tone: Color, hoverProgress: Float,
    ) {
        scope.iconButton(bounds, label, scale, tone, hoverProgress)
    }

    /**
     * 标准 filled field 的语义色值集合。
     *
     * @param text      文本颜色
     * @param caret     光标颜色
     * @param indicator 底部指示线颜色
     */
    data class FilledFieldColors(val text: Color, val caret: Color, val indicator: Color)

}
