package com.github.epsilon.gui.dropdown

import com.github.epsilon.gui.theme.MD3Theme
import java.awt.Color

object DropdownTheme {

    const val PANEL_WIDTH = 130.0f
    const val PANEL_HEADER_HEIGHT = 28.0f
    const val PANEL_RADIUS = 10.0f
    const val PANEL_GAP = 14.0f
    const val PANEL_MARGIN_X = 20.0f
    const val PANEL_MARGIN_Y = 20.0f
    const val PANEL_SHADOW_BLUR = 20.0f
    const val PANEL_SHADOW_ALPHA = 96

    const val GROUP_HEADER_HEIGHT = 18.0f
    const val GROUP_INSET = 4.0f
    const val GROUP_NEST_INSET = 4.0f
    const val GROUP_CARD_RADIUS = 8.0f
    const val GROUP_CARD_MIN_WIDTH = 42.0f
    const val GROUP_DEPTH_LIMIT = 3
    const val GROUP_HEADER_TEXT_SCALE = 0.52f
    const val GROUP_COUNT_CHIP_HEIGHT = 11.0f
    const val GROUP_COUNT_CHIP_PADDING = 6.0f
    const val GROUP_COUNT_TEXT_SCALE = 0.42f

    const val MODULE_HEIGHT = 19.0f
    const val MODULE_PADDING_X = 7.0f
    const val MODULE_TEXT_SCALE = 0.7f

    const val SETTING_PADDING_X = 6.0f
    const val SETTING_HEIGHT = 16.0f
    const val SETTING_TEXT_SCALE = 0.65f
    const val SETTING_GAP = 3.0f
    const val SETTING_INDENT = 5.0f

    const val SLIDER_HEIGHT = 3.0f
    const val SLIDER_RADIUS = 1.5f
    const val SLIDER_KNOB_RADIUS = 3.5f

    const val COLOR_PREVIEW_SIZE = 12.0f

    const val KEYBIND_WIDTH = 34.0f
    const val KEYBIND_HEIGHT = 14.0f
    const val KEYBIND_RADIUS = 5.0f

    const val INPUT_HEIGHT = 16.0f
    const val INPUT_RADIUS = 5.0f

    const val BUTTON_HEIGHT = 16.0f
    const val BUTTON_RADIUS = 5.0f

    const val SCROLL_SPEED = 28.0f

    const val PANEL_BOTTOM_PADDING = 8.0f

    const val ANIM_OPEN = 200L
    const val ANIM_TOGGLE = 180L
    const val ANIM_HOVER = 120L
    const val ANIM_EXPAND = 220L
    const val ANIM_GROUP = 180L

    const val HEADER_TEXT_SCALE = 0.82f
    const val HEADER_ICON_SCALE = 0.86f

    fun panelBackground(): Color {
        return MD3Theme.SURFACE_CONTAINER
    }

    fun panelShadow(): Color {
        return MD3Theme.withAlpha(MD3Theme.SHADOW, PANEL_SHADOW_ALPHA)
    }

    fun moduleDivider(): Color {
        return MD3Theme.withAlpha(MD3Theme.OUTLINE, 24)
    }

    fun moduleEnabled(hoverProgress: Float): Color {
        return MD3Theme.lerp(MD3Theme.PRIMARY_CONTAINER, MD3Theme.lerp(MD3Theme.PRIMARY_CONTAINER, MD3Theme.PRIMARY, 0.15f), hoverProgress)
    }

    fun moduleDisabled(hoverProgress: Float): Color {
        return MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER, MD3Theme.SURFACE_CONTAINER_HIGH, hoverProgress)
    }

    fun moduleTextEnabled(): Color {
        return MD3Theme.ON_PRIMARY_CONTAINER
    }

    fun moduleTextDisabled(hoverProgress: Float): Color {
        return MD3Theme.lerp(MD3Theme.TEXT_SECONDARY, MD3Theme.TEXT_PRIMARY, hoverProgress)
    }

    fun settingLabel(): Color {
        return MD3Theme.TEXT_PRIMARY
    }

    fun settingLabelMuted(): Color {
        return MD3Theme.TEXT_MUTED
    }

    fun settingSurface(): Color {
        return MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 160)
    }

    fun sliderTrack(): Color {
        return MD3Theme.SURFACE_CONTAINER_HIGHEST
    }

    fun sliderActive(): Color {
        return MD3Theme.PRIMARY
    }

    fun sliderKnob(): Color {
        return MD3Theme.PRIMARY
    }

    fun keybindSurface(listening: Boolean): Color {
        return if (listening) MD3Theme.PRIMARY_CONTAINER else MD3Theme.SURFACE_CONTAINER_HIGHEST
    }

    fun keybindText(listening: Boolean): Color {
        return if (listening) MD3Theme.ON_PRIMARY_CONTAINER else MD3Theme.TEXT_PRIMARY
    }

    fun inputSurface(focused: Boolean): Color {
        return if (focused) MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER_HIGH, MD3Theme.PRIMARY_CONTAINER, 0.3f) else MD3Theme.SURFACE_CONTAINER_HIGH
    }

    fun inputText(): Color {
        return MD3Theme.TEXT_PRIMARY
    }

    fun inputIndicator(focused: Boolean): Color {
        return if (focused) MD3Theme.PRIMARY else MD3Theme.withAlpha(MD3Theme.OUTLINE, 96)
    }

    fun buttonSurface(hoverProgress: Float): Color {
        return MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.SECONDARY, 0.12f), hoverProgress)
    }

    fun buttonText(): Color {
        return MD3Theme.ON_SECONDARY_CONTAINER
    }

    fun expandArrow(toggleProgress: Float): Color {
        return MD3Theme.lerp(MD3Theme.TEXT_MUTED, MD3Theme.ON_PRIMARY_CONTAINER, toggleProgress)
    }

    fun scrollbar(): Color {
        return MD3Theme.withAlpha(MD3Theme.OUTLINE, 64)
    }

    fun scrollbar(hoverProgress: Float): Color {
        return MD3Theme.lerp(scrollbar(), MD3Theme.withAlpha(MD3Theme.PRIMARY, 190), hoverProgress)
    }

    fun scrim(): Color {
        return Color(0, 0, 0, 50)
    }

    fun groupText(): Color {
        return MD3Theme.TEXT_PRIMARY
    }

    fun groupCountChip(): Color {
        return MD3Theme.withAlpha(MD3Theme.SECONDARY_CONTAINER, 210)
    }

    fun groupCountText(): Color {
        return MD3Theme.ON_SECONDARY_CONTAINER
    }

    fun groupChevron(hoverProgress: Float): Color {
        return MD3Theme.lerp(MD3Theme.TEXT_MUTED, MD3Theme.PRIMARY, hoverProgress)
    }

    /**
     * 展开分组的整块卡片背景；层级越深表面色越浅，用于说明 Setting 属于该分组。
     */
    fun groupCardBackground(depth: Int): Color {
        val ratio = depthRatio(depth)
        return MD3Theme.withAlpha(MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER_LOW, MD3Theme.SURFACE_CONTAINER_HIGH, ratio * 0.65f), 200)
    }

    /**
     * 分组卡片描边；层级越深越明显，但受 [GROUP_DEPTH_LIMIT] 限制。
     */
    fun groupCardOutline(depth: Int): Color {
        return MD3Theme.withAlpha(MD3Theme.OUTLINE, 32 + 10 * minOf(maxOf(depth, 0), GROUP_DEPTH_LIMIT))
    }

    /**
     * 分组标题行的悬浮叠加层。
     */
    fun groupHeaderHover(hoverProgress: Float): Color {
        return MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hoverProgress, if (MD3Theme.isLightTheme()) 10 else 14)
    }

    /**
     * 分组卡片圆角：折叠时保持胶囊样式，展开后过渡到卡片圆角。
     */
    fun groupCardRadius(expandProgress: Float): Float {
        val progress = maxOf(0.0f, minOf(1.0f, expandProgress))
        return BUTTON_RADIUS + (GROUP_CARD_RADIUS - BUTTON_RADIUS) * progress
    }

    /**
     * 嵌套层级缩进；达到 [GROUP_DEPTH_LIMIT] 后不再增加，并在宽度不足时收敛。
     */
    fun groupNestInset(depth: Int, availableWidth: Float): Float {
        val limit = maxOf(0.0f, (availableWidth - GROUP_CARD_MIN_WIDTH) * 0.5f)
        val clampedDepth = minOf(maxOf(depth, 0), GROUP_DEPTH_LIMIT)
        return minOf(GROUP_NEST_INSET * clampedDepth, limit)
    }

    private fun depthRatio(depth: Int): Float {
        val clampedDepth = minOf(maxOf(depth, 0), GROUP_DEPTH_LIMIT)
        return clampedDepth / GROUP_DEPTH_LIMIT.toFloat()
    }

    fun groupDivider(): Color {
        return MD3Theme.withAlpha(MD3Theme.OUTLINE, 48)
    }

}
