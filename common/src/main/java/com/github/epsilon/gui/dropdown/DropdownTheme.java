package com.github.epsilon.gui.dropdown;

import com.github.epsilon.gui.theme.MD3Theme;

import java.awt.*;

public class DropdownTheme {

    public static final float PANEL_WIDTH = 130.0f;
    public static final float PANEL_HEADER_HEIGHT = 28.0f;
    public static final float PANEL_RADIUS = 10.0f;
    public static final float PANEL_GAP = 14.0f;
    public static final float PANEL_MARGIN_X = 20.0f;
    public static final float PANEL_MARGIN_Y = 20.0f;
    public static final float PANEL_SHADOW_BLUR = 20.0f;
    public static final int PANEL_SHADOW_ALPHA = 96;

    public static final float GROUP_HEADER_HEIGHT = 18.0f;
    public static final float GROUP_INSET = 4.0f;
    public static final float GROUP_NEST_INSET = 4.0f;
    public static final float GROUP_CARD_RADIUS = 8.0f;
    public static final float GROUP_CARD_MIN_WIDTH = 42.0f;
    public static final int GROUP_DEPTH_LIMIT = 3;
    public static final float GROUP_HEADER_TEXT_SCALE = 0.52f;
    public static final float GROUP_COUNT_CHIP_HEIGHT = 11.0f;
    public static final float GROUP_COUNT_CHIP_PADDING = 6.0f;
    public static final float GROUP_COUNT_TEXT_SCALE = 0.42f;

    public static final float MODULE_HEIGHT = 19.0f;
    public static final float MODULE_PADDING_X = 7.0f;
    public static final float MODULE_TEXT_SCALE = 0.7f;

    public static final float SETTING_PADDING_X = 6.0f;
    public static final float SETTING_HEIGHT = 16.0f;
    public static final float SETTING_TEXT_SCALE = 0.65f;
    public static final float SETTING_GAP = 3.0f;
    public static final float SETTING_INDENT = 5.0f;

    public static final float SLIDER_HEIGHT = 3.0f;
    public static final float SLIDER_RADIUS = 1.5f;
    public static final float SLIDER_KNOB_RADIUS = 3.5f;

    public static final float COLOR_PREVIEW_SIZE = 12.0f;

    public static final float KEYBIND_WIDTH = 34.0f;
    public static final float KEYBIND_HEIGHT = 14.0f;
    public static final float KEYBIND_RADIUS = 5.0f;

    public static final float INPUT_HEIGHT = 16.0f;
    public static final float INPUT_RADIUS = 5.0f;

    public static final float BUTTON_HEIGHT = 16.0f;
    public static final float BUTTON_RADIUS = 5.0f;

    public static final float SCROLL_SPEED = 28.0f;

    public static final float PANEL_BOTTOM_PADDING = 8.0f;

    public static final long ANIM_OPEN = 200L;
    public static final long ANIM_TOGGLE = 180L;
    public static final long ANIM_HOVER = 120L;
    public static final long ANIM_EXPAND = 220L;
    public static final long ANIM_GROUP = 180L;

    public static final float HEADER_TEXT_SCALE = 0.82f;
    public static final float HEADER_ICON_SCALE = 0.86f;

    private DropdownTheme() {
    }

    public static Color panelBackground() {
        return MD3Theme.SURFACE_CONTAINER;
    }

    public static Color panelShadow() {
        return MD3Theme.withAlpha(MD3Theme.SHADOW, PANEL_SHADOW_ALPHA);
    }

    public static Color moduleDivider() {
        return MD3Theme.withAlpha(MD3Theme.OUTLINE, 24);
    }

    public static Color moduleEnabled(float hoverProgress) {
        return MD3Theme.lerp(MD3Theme.PRIMARY_CONTAINER, MD3Theme.lerp(MD3Theme.PRIMARY_CONTAINER, MD3Theme.PRIMARY, 0.15f), hoverProgress);
    }

    public static Color moduleDisabled(float hoverProgress) {
        return MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER, MD3Theme.SURFACE_CONTAINER_HIGH, hoverProgress);
    }

    public static Color moduleTextEnabled() {
        return MD3Theme.ON_PRIMARY_CONTAINER;
    }

    public static Color moduleTextDisabled(float hoverProgress) {
        return MD3Theme.lerp(MD3Theme.TEXT_SECONDARY, MD3Theme.TEXT_PRIMARY, hoverProgress);
    }

    public static Color settingLabel() {
        return MD3Theme.TEXT_PRIMARY;
    }

    public static Color settingLabelMuted() {
        return MD3Theme.TEXT_MUTED;
    }

    public static Color settingSurface() {
        return MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 160);
    }

    public static Color sliderTrack() {
        return MD3Theme.SURFACE_CONTAINER_HIGHEST;
    }

    public static Color sliderActive() {
        return MD3Theme.PRIMARY;
    }

    public static Color sliderKnob() {
        return MD3Theme.PRIMARY;
    }

    public static Color keybindSurface(boolean listening) {
        return listening ? MD3Theme.PRIMARY_CONTAINER : MD3Theme.SURFACE_CONTAINER_HIGHEST;
    }

    public static Color keybindText(boolean listening) {
        return listening ? MD3Theme.ON_PRIMARY_CONTAINER : MD3Theme.TEXT_PRIMARY;
    }

    public static Color inputSurface(boolean focused) {
        return focused ? MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER_HIGH, MD3Theme.PRIMARY_CONTAINER, 0.3f) : MD3Theme.SURFACE_CONTAINER_HIGH;
    }

    public static Color inputText() {
        return MD3Theme.TEXT_PRIMARY;
    }

    public static Color inputIndicator(boolean focused) {
        return focused ? MD3Theme.PRIMARY : MD3Theme.withAlpha(MD3Theme.OUTLINE, 96);
    }

    public static Color buttonSurface(float hoverProgress) {
        return MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.SECONDARY, 0.12f), hoverProgress);
    }

    public static Color buttonText() {
        return MD3Theme.ON_SECONDARY_CONTAINER;
    }

    public static Color expandArrow(float toggleProgress) {
        return MD3Theme.lerp(MD3Theme.TEXT_MUTED, MD3Theme.ON_PRIMARY_CONTAINER, toggleProgress);
    }

    public static Color scrollbar() {
        return MD3Theme.withAlpha(MD3Theme.OUTLINE, 64);
    }

    public static Color scrollbar(float hoverProgress) {
        return MD3Theme.lerp(scrollbar(), MD3Theme.withAlpha(MD3Theme.PRIMARY, 190), hoverProgress);
    }

    public static Color scrim() {
        return new Color(0, 0, 0, 50);
    }

    public static Color groupText() {
        return MD3Theme.TEXT_PRIMARY;
    }

    public static Color groupCountChip() {
        return MD3Theme.withAlpha(MD3Theme.SECONDARY_CONTAINER, 210);
    }

    public static Color groupCountText() {
        return MD3Theme.ON_SECONDARY_CONTAINER;
    }

    public static Color groupChevron(float hoverProgress) {
        return MD3Theme.lerp(MD3Theme.TEXT_MUTED, MD3Theme.PRIMARY, hoverProgress);
    }

    /**
     * 展开分组的整块卡片背景；层级越深表面色越浅，用于说明 Setting 属于该分组。
     */
    public static Color groupCardBackground(int depth) {
        float ratio = depthRatio(depth);
        return MD3Theme.withAlpha(MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER_LOW, MD3Theme.SURFACE_CONTAINER_HIGH, ratio * 0.65f), 200);
    }

    /**
     * 分组卡片描边；层级越深越明显，但受 {@link #GROUP_DEPTH_LIMIT} 限制。
     */
    public static Color groupCardOutline(int depth) {
        return MD3Theme.withAlpha(MD3Theme.OUTLINE, 32 + 10 * Math.min(Math.max(depth, 0), GROUP_DEPTH_LIMIT));
    }

    /**
     * 分组标题行的悬浮叠加层。
     */
    public static Color groupHeaderHover(float hoverProgress) {
        return MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hoverProgress, MD3Theme.isLightTheme() ? 10 : 14);
    }

    /**
     * 分组卡片圆角：折叠时保持胶囊样式，展开后过渡到卡片圆角。
     */
    public static float groupCardRadius(float expandProgress) {
        float progress = Math.max(0.0f, Math.min(1.0f, expandProgress));
        return BUTTON_RADIUS + (GROUP_CARD_RADIUS - BUTTON_RADIUS) * progress;
    }

    /**
     * 嵌套层级缩进；达到 {@link #GROUP_DEPTH_LIMIT} 后不再增加，并在宽度不足时收敛。
     */
    public static float groupNestInset(int depth, float availableWidth) {
        float limit = Math.max(0.0f, (availableWidth - GROUP_CARD_MIN_WIDTH) * 0.5f);
        int clampedDepth = Math.min(Math.max(depth, 0), GROUP_DEPTH_LIMIT);
        return Math.min(GROUP_NEST_INSET * clampedDepth, limit);
    }

    private static float depthRatio(int depth) {
        int clampedDepth = Math.min(Math.max(depth, 0), GROUP_DEPTH_LIMIT);
        return clampedDepth / (float) GROUP_DEPTH_LIMIT;
    }

    public static Color groupDivider() {
        return MD3Theme.withAlpha(MD3Theme.OUTLINE, 48);
    }

}
