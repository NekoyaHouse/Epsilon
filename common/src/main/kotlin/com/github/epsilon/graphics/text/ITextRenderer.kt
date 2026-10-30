package com.github.epsilon.graphics.text

import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.mojang.renderpearl.api.commands.RenderPass
import java.awt.Color

interface ITextRenderer {

    fun addText(text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader)

    fun addBlurredText(
        text: String,
        x: Float,
        y: Float,
        scale: Float,
        color: Color,
        blurRadius: Float,
        fontLoader: TtfFontLoader
    ) {
        addText(text, x, y, scale, color, fontLoader)
    }

    fun addGlitchText(
        text: String,
        x: Float,
        y: Float,
        scale: Float,
        color: Color,
        effect: TextGlitchEffect?,
        fontLoader: TtfFontLoader
    ) {
        addText(text, x, y, scale, color, fontLoader)
    }

    fun addRotatedText(
        text: String,
        x: Float,
        y: Float,
        scale: Float,
        color: Color,
        fontLoader: TtfFontLoader,
        originX: Float,
        originY: Float,
        rotationDegrees: Float
    ) {
        addText(text, x, y, scale, color, fontLoader)
    }

    fun addGradientText(
        text: String,
        x: Float,
        y: Float,
        scale: Float,
        startColor: Color,
        endColor: Color,
        fontLoader: TtfFontLoader
    ) {
        addText(text, x, y, scale, startColor, fontLoader)
    }

    fun draw()

    /**
     * 在共享 RenderPass 打开前完成所有缓冲映射、uniform 写入和 atlas 准备。
     */
    fun prepareSharedDraw(): Boolean {
        return false
    }

    /**
     * 写入已经绑定好 pipeline 的 RenderPass，只允许绑定状态并提交 draw。
     */
    fun draw(pass: RenderPass) {
    }

    fun clear()

    fun close()

    fun getHeight(scale: Float, fontLoader: TtfFontLoader): Float

    fun getWidth(text: String, scale: Float, fontLoader: TtfFontLoader): Float

    fun setScissor(x: Int, y: Int, width: Int, height: Int) {
    }

    fun clearScissor() {
    }

}
