package com.github.epsilon.gui.lib;

import com.github.epsilon.graphics.text.ttf.TtfFontLoader;

/**
 * 布局代码使用的最小文本度量接口。
 * <p>
 * 宿主可以基于 Screen、Lumin renderer 或测试桩实现该接口，而无需把具体 renderer 传给组件。
 */
public interface UiTextMetrics {

    float textWidth(String text, float scale);

    float textWidth(String text, float scale, TtfFontLoader fontLoader);

    float textHeight(float scale);

    float textHeight(float scale, TtfFontLoader fontLoader);
}
