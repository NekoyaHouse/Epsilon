package com.github.epsilon.gui.lib

import java.awt.Color

/**
 * GUI 库绘制语义控件时使用的主题契约。
 *
 * 库只依赖该接口，不读取 Epsilon 的模块或设置。宿主可以在运行时提供任意主题实现。
 */
interface UiTheme {

    fun controlRadius(): Float

    fun textPrimary(): Color

    fun textMuted(): Color

    fun outlineSoft(): Color

    fun stateLayer(color: Color, progress: Float, maxAlpha: Int): Color

    fun lerp(start: Color, end: Color, delta: Float): Color

    fun withAlpha(color: Color, alpha: Int): Color

    fun light(): Boolean

    fun filledFieldSurface(focused: Boolean, hoverProgress: Float): Color

    fun segmentedControlSurface(): Color

    fun segmentedControlIndicator(): Color

    fun segmentedControlActiveLabel(): Color

    fun segmentedControlInactiveLabel(): Color

    fun switchTrack(toggleProgress: Float): Color

    fun switchKnob(toggleProgress: Float): Color

    fun switchTrackOutline(toggleProgress: Float, hoverProgress: Float): Color

    fun switchTrackOutlineWidth(toggleProgress: Float): Float

    fun switchHandleSizeOff(): Float

    fun switchHandleSizeOn(): Float

    fun switchHandleInsetOff(): Float

    fun switchHandleInsetOn(): Float

    fun switchStateLayerSize(): Float

    fun scrollBar(hoverProgress: Float): Color

    fun hoverAnimationDuration(): Long

}
