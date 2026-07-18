package com.github.epsilon.gui.lib;

import java.awt.*;

/**
 * GUI 库绘制语义控件时使用的主题契约。
 * <p>
 * 库只依赖该接口，不读取 Epsilon 的模块或设置。宿主可以在运行时提供任意主题实现。
 */
public interface UiTheme {

    float controlRadius();

    Color textPrimary();

    Color textMuted();

    Color outlineSoft();

    Color stateLayer(Color color, float progress, int maxAlpha);

    Color lerp(Color start, Color end, float delta);

    Color withAlpha(Color color, int alpha);

    boolean light();

    Color filledFieldSurface(boolean focused, float hoverProgress);

    Color segmentedControlSurface();

    Color segmentedControlIndicator();

    Color segmentedControlActiveLabel();

    Color segmentedControlInactiveLabel();

    Color switchTrack(float toggleProgress);

    Color switchKnob(float toggleProgress);

    Color switchTrackOutline(float toggleProgress, float hoverProgress);

    float switchTrackOutlineWidth(float toggleProgress);

    float switchHandleSizeOff();

    float switchHandleSizeOn();

    float switchHandleInsetOff();

    float switchHandleInsetOn();

    float switchStateLayerSize();

    Color scrollBar(float hoverProgress);

    long hoverAnimationDuration();
}
