package com.github.epsilon.gui.theme;

import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.lib.UiTheme;

import java.awt.*;

/**
 * 将 Epsilon 的动态 Material 主题适配到独立 GUI 库。
 */
public final class EpsilonUiTheme implements UiTheme {

    public static final EpsilonUiTheme INSTANCE = new EpsilonUiTheme();

    private EpsilonUiTheme() {
    }

    @Override
    public float controlRadius() {
        return MD3Theme.CONTROL_RADIUS;
    }

    @Override
    public Color textPrimary() {
        return MD3Theme.TEXT_PRIMARY;
    }

    @Override
    public Color textMuted() {
        return MD3Theme.TEXT_MUTED;
    }

    @Override
    public Color outlineSoft() {
        return MD3Theme.OUTLINE_SOFT;
    }

    @Override
    public Color stateLayer(Color color, float progress, int maxAlpha) {
        return MD3Theme.stateLayer(color, progress, maxAlpha);
    }

    @Override
    public Color lerp(Color start, Color end, float delta) {
        return MD3Theme.lerp(start, end, delta);
    }

    @Override
    public Color withAlpha(Color color, int alpha) {
        return MD3Theme.withAlpha(color, alpha);
    }

    @Override
    public boolean light() {
        return MD3Theme.isLightTheme();
    }

    @Override
    public Color filledFieldSurface(boolean focused, float hoverProgress) {
        return MD3Theme.filledFieldSurface(focused, hoverProgress);
    }

    @Override
    public Color segmentedControlSurface() {
        return MD3Theme.segmentedControlSurface();
    }

    @Override
    public Color segmentedControlIndicator() {
        return MD3Theme.segmentedControlIndicator();
    }

    @Override
    public Color segmentedControlActiveLabel() {
        return MD3Theme.segmentedControlActiveLabel();
    }

    @Override
    public Color segmentedControlInactiveLabel() {
        return MD3Theme.segmentedControlInactiveLabel();
    }

    @Override
    public Color switchTrack(float toggleProgress) {
        return MD3Theme.switchTrack(toggleProgress);
    }

    @Override
    public Color switchKnob(float toggleProgress) {
        return MD3Theme.switchKnob(toggleProgress);
    }

    @Override
    public Color switchTrackOutline(float toggleProgress, float hoverProgress) {
        return MD3Theme.switchTrackOutline(toggleProgress, hoverProgress);
    }

    @Override
    public float switchTrackOutlineWidth(float toggleProgress) {
        return MD3Theme.switchTrackOutlineWidth(toggleProgress);
    }

    @Override
    public float switchHandleSizeOff() {
        return MD3Theme.SWITCH_HANDLE_SIZE_OFF;
    }

    @Override
    public float switchHandleSizeOn() {
        return MD3Theme.SWITCH_HANDLE_SIZE_ON;
    }

    @Override
    public float switchHandleInsetOff() {
        return MD3Theme.SWITCH_HANDLE_INSET_OFF;
    }

    @Override
    public float switchHandleInsetOn() {
        return MD3Theme.SWITCH_HANDLE_INSET_ON;
    }

    @Override
    public float switchStateLayerSize() {
        return MD3Theme.SWITCH_STATE_LAYER_SIZE;
    }

    @Override
    public Color scrollBar(float hoverProgress) {
        return DropdownTheme.scrollbar(hoverProgress);
    }

    @Override
    public long hoverAnimationDuration() {
        return DropdownTheme.ANIM_HOVER;
    }
}
