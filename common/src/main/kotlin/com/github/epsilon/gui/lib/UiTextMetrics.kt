package com.github.epsilon.gui.lib

import com.github.epsilon.graphics.text.ttf.TtfFontLoader

/**
 * 布局代码使用的最小文本度量接口。
 *
 * 宿主可以基于 Screen、Lumin renderer 或测试桩实现该接口，而无需把具体 renderer 传给组件。
 */
interface UiTextMetrics {

    fun textWidth(text: String, scale: Float): Float

    fun textWidth(text: String, scale: Float, fontLoader: TtfFontLoader): Float

    fun textHeight(scale: Float): Float

    fun textHeight(scale: Float, fontLoader: TtfFontLoader): Float

}
