package com.github.epsilon.gui.theme

import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.lib.UiTheme
import java.awt.Color

/**
 * 将 Epsilon 的动态 Material 主题适配到独立 GUI 库。
 */
object EpsilonUiTheme : UiTheme {

    override fun controlRadius(): Float {
        return MD3Theme.CONTROL_RADIUS
    }

    override fun textPrimary(): Color {
        return MD3Theme.TEXT_PRIMARY
    }

    override fun textMuted(): Color {
        return MD3Theme.TEXT_MUTED
    }

    override fun outlineSoft(): Color {
        return MD3Theme.OUTLINE_SOFT
    }

    override fun stateLayer(color: Color, progress: Float, maxAlpha: Int): Color {
        return MD3Theme.stateLayer(color, progress, maxAlpha)
    }

    override fun lerp(start: Color, end: Color, delta: Float): Color {
        return MD3Theme.lerp(start, end, delta)
    }

    override fun withAlpha(color: Color, alpha: Int): Color {
        return MD3Theme.withAlpha(color, alpha)
    }

    override fun light(): Boolean {
        return MD3Theme.isLightTheme()
    }

    override fun filledFieldSurface(focused: Boolean, hoverProgress: Float): Color {
        return MD3Theme.filledFieldSurface(focused, hoverProgress)
    }

    override fun segmentedControlSurface(): Color {
        return MD3Theme.segmentedControlSurface()
    }

    override fun segmentedControlIndicator(): Color {
        return MD3Theme.segmentedControlIndicator()
    }

    override fun segmentedControlActiveLabel(): Color {
        return MD3Theme.segmentedControlActiveLabel()
    }

    override fun segmentedControlInactiveLabel(): Color {
        return MD3Theme.segmentedControlInactiveLabel()
    }

    override fun switchTrack(toggleProgress: Float): Color {
        return MD3Theme.switchTrack(toggleProgress)
    }

    override fun switchKnob(toggleProgress: Float): Color {
        return MD3Theme.switchKnob(toggleProgress)
    }

    override fun switchTrackOutline(toggleProgress: Float, hoverProgress: Float): Color {
        return MD3Theme.switchTrackOutline(toggleProgress, hoverProgress)
    }

    override fun switchTrackOutlineWidth(toggleProgress: Float): Float {
        return MD3Theme.switchTrackOutlineWidth(toggleProgress)
    }

    override fun switchHandleSizeOff(): Float {
        return MD3Theme.SWITCH_HANDLE_SIZE_OFF
    }

    override fun switchHandleSizeOn(): Float {
        return MD3Theme.SWITCH_HANDLE_SIZE_ON
    }

    override fun switchHandleInsetOff(): Float {
        return MD3Theme.SWITCH_HANDLE_INSET_OFF
    }

    override fun switchHandleInsetOn(): Float {
        return MD3Theme.SWITCH_HANDLE_INSET_ON
    }

    override fun switchStateLayerSize(): Float {
        return MD3Theme.SWITCH_STATE_LAYER_SIZE
    }

    override fun scrollBar(hoverProgress: Float): Color {
        return DropdownTheme.scrollbar(hoverProgress)
    }

    override fun hoverAnimationDuration(): Long {
        return DropdownTheme.ANIM_HOVER
    }

}
