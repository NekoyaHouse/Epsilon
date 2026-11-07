package com.github.epsilon.gui.panel.component

import com.github.epsilon.gui.lib.RoundRect
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.theme.MD3Theme

/**
 * 面板通用组件辅助方法集合。
 *
 * 几何辅助函数分成两套坐标语义：
 * - `xxxBounds(bounds, ...)` 接收并返回**绝对**矩形，供命中测试与弹窗定位使用；
 * - `localXxx` 使用行作用域的**局部**坐标（原点 = 行左上角），供绘制使用。
 *
 * 两者共用同一份尺寸常量，避免绘制与命中区域漂移。
 */
object PanelElements {

    const val ROW_LABEL_INSET: Float = MD3Theme.ROW_CONTENT_INSET + 4.0f

    const val ICON_BUTTON_SIZE: Float = 20.0f

    /**
     * 计算一行内容中主标签的起始 X 坐标。
     */
    fun rowLabelX(bounds: UiRect): Float = bounds.x + ROW_LABEL_INSET

    /**
     * 将一个控件对齐到行尾区域，并在垂直方向居中（绝对坐标）。
     */
    fun alignTrailing(bounds: UiRect, width: Float, height: Float): UiRect =
        localAlignTrailing(bounds.width, bounds.height, width, height).translate(bounds.x, bounds.y)

    /**
     * 将一个控件对齐到行尾区域，并在垂直方向居中（行局部坐标）。
     */
    fun localAlignTrailing(rowWidth: Float, rowHeight: Float, width: Float, height: Float): UiRect = UiRect(
        rowWidth - MD3Theme.ROW_TRAILING_INSET - width,
        (rowHeight - height) / 2.0f,
        width,
        height
    )

    /**
     * 计算行内开关控件的标准区域（绝对坐标）。
     */
    fun switchBounds(bounds: UiRect): UiRect =
        alignTrailing(bounds, MD3Theme.SWITCH_WIDTH, MD3Theme.SWITCH_HEIGHT)

    /**
     * 计算行内开关控件的标准区域（行局部坐标）。
     */
    fun localSwitchBounds(rowWidth: Float, rowHeight: Float): UiRect =
        localAlignTrailing(rowWidth, rowHeight, MD3Theme.SWITCH_WIDTH, MD3Theme.SWITCH_HEIGHT)

    fun compactFieldBounds(bounds: UiRect, width: Float): UiRect =
        alignTrailing(bounds, width, MD3Theme.CONTROL_HEIGHT)

    /**
     * 测量 assist chip 的绝对矩形（命中测试/弹窗定位用）。
     */
    fun measureAssistChipBounds(
        metrics: UiTextMetrics, rowBounds: UiRect, label: String,
        textScale: Float, horizontalPadding: Float, trailingSlotWidth: Float, maxWidth: Float,
    ): UiRect = measureLocalAssistChipBounds(
        metrics, rowBounds.width, rowBounds.height, label,
        textScale, horizontalPadding, trailingSlotWidth, maxWidth
    ).translate(rowBounds.x, rowBounds.y)

    /**
     * 测量 assist chip 的行局部矩形（绘制用）。
     */
    fun measureLocalAssistChipBounds(
        metrics: UiTextMetrics, rowWidth: Float, rowHeight: Float, label: String,
        textScale: Float, horizontalPadding: Float, trailingSlotWidth: Float, maxWidth: Float,
    ): UiRect {
        val desiredWidth = metrics.textWidth(label, textScale) + horizontalPadding * 2.0f + trailingSlotWidth
        return localAlignTrailing(rowWidth, rowHeight, minOf(maxWidth, desiredWidth), MD3Theme.COMPACT_CHIP_HEIGHT)
    }

}

/**
 * 在行作用域中绘制整行底色。
 */
fun UiScope.RowSurface(hoverProgress: Float) {
    RoundRect(0.0f, 0.0f, width, height, MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.rowSurface(hoverProgress))
}
