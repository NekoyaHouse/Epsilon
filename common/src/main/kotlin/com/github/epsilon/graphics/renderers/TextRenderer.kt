package com.github.epsilon.graphics.renderers

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.text.ITextRenderer
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.graphics.text.TextGlitchEffect
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.graphics.text.ttf.TtfTextRenderer
import com.github.epsilon.managers.RendererManager
import com.mojang.renderpearl.api.commands.RenderPass
import java.awt.Color

class TextRenderer : IRenderer {

    private val textRenderer: ITextRenderer
    private var registered = true

    private constructor(bufferSize: Long) {
        textRenderer = TtfTextRenderer(bufferSize)
    }

    private constructor(bufferSize: Long, fontBlur: Boolean) {
        textRenderer = TtfTextRenderer(bufferSize, fontBlur)
    }

    private constructor() {
        textRenderer = TtfTextRenderer()
    }

    private constructor(bufferSize: Long, mode: TtfTextRenderer.Mode) {
        textRenderer = TtfTextRenderer(bufferSize, mode)
    }

    companion object {

        fun create(bufferSize: Long): TextRenderer = RendererManager.INSTANCE.register(TextRenderer(bufferSize))

        fun create(): TextRenderer = RendererManager.INSTANCE.register(TextRenderer())

        fun createFontBlur(): TextRenderer = RendererManager.INSTANCE.register(TextRenderer(64 * 1024L, true))

        fun createGlitch(): TextRenderer =
            RendererManager.INSTANCE.register(TextRenderer(64 * 1024L, TtfTextRenderer.Mode.GLITCH))

    }

    fun addText(text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader) {
        ensureRegistered()
        textRenderer.addText(text, x, y, scale, color, fontLoader)
    }

    fun addBlurredText(
        text: String, x: Float, y: Float, scale: Float, color: Color,
        blurRadius: Float, intensity: Int, fontLoader: TtfFontLoader,
    ) {
        ensureRegistered()
        for (i in 0 until intensity) {
            textRenderer.addBlurredText(text, x, y, scale, color, blurRadius, fontLoader)
        }
    }

    fun addBlurredText(text: String, x: Float, y: Float, scale: Float, color: Color, blurRadius: Float, intensity: Int) {
        addBlurredText(text, x, y, scale, color, blurRadius, intensity, StaticFontLoader.defaultFont())
    }

    fun addGlitchText(
        text: String, x: Float, y: Float, scale: Float, color: Color,
        effect: TextGlitchEffect?, fontLoader: TtfFontLoader,
    ) {
        ensureRegistered()
        textRenderer.addGlitchText(text, x, y, scale, color, effect, fontLoader)
    }

    fun addGlitchText(text: String, x: Float, y: Float, scale: Float, color: Color, effect: TextGlitchEffect?) {
        addGlitchText(text, x, y, scale, color, effect, StaticFontLoader.defaultFont())
    }

    fun addGradientText(
        text: String, x: Float, y: Float, scale: Float,
        startColor: Color, endColor: Color, fontLoader: TtfFontLoader,
    ) {
        ensureRegistered()
        textRenderer.addGradientText(text, x, y, scale, startColor, endColor, fontLoader)
    }

    fun addRotatedText(
        text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader,
        originX: Float, originY: Float, rotationDegrees: Float,
    ) {
        ensureRegistered()
        textRenderer.addRotatedText(text, x, y, scale, color, fontLoader, originX, originY, rotationDegrees)
    }

    fun addText(text: String, x: Float, y: Float, scale: Float, color: Color) {
        textRenderer.addText(text, x, y, scale, color, StaticFontLoader.defaultFont())
    }

    fun addRotatedText(
        text: String, x: Float, y: Float, scale: Float, color: Color,
        originX: Float, originY: Float, rotationDegrees: Float,
    ) {
        textRenderer.addRotatedText(
            text, x, y, scale, color, StaticFontLoader.defaultFont(), originX, originY, rotationDegrees
        )
    }

    fun addGradientText(text: String, x: Float, y: Float, scale: Float, startColor: Color, endColor: Color) {
        textRenderer.addGradientText(text, x, y, scale, startColor, endColor, StaticFontLoader.defaultFont())
    }

    fun addText(text: String, x: Float, y: Float, color: Color, fontLoader: TtfFontLoader) {
        textRenderer.addText(text, x, y, 1.0f, color, fontLoader)
    }

    fun addText(text: String, x: Float, y: Float, color: Color) {
        textRenderer.addText(text, x, y, 1.0f, color, StaticFontLoader.defaultFont())
    }

    fun getHeight(scale: Float): Float {
        return textRenderer.getHeight(scale, StaticFontLoader.defaultFont())
    }

    fun getHeight(scale: Float, fontLoader: TtfFontLoader): Float {
        return textRenderer.getHeight(scale, fontLoader)
    }

    fun getWidth(text: String, scale: Float): Float {
        return textRenderer.getWidth(text, scale, StaticFontLoader.defaultFont())
    }

    fun getWidth(text: String, scale: Float, fontLoader: TtfFontLoader): Float {
        return textRenderer.getWidth(text, scale, fontLoader)
    }

    fun setScissor(x: Int, y: Int, width: Int, height: Int) {
        textRenderer.setScissor(x, y, width, height)
    }

    fun clearScissor() {
        textRenderer.clearScissor()
    }

    override fun draw() {
        LuminRenderSystem.applyOrthoProjection()
        textRenderer.draw()
    }

    override fun prepareSharedDraw(): Boolean {
        return textRenderer.prepareSharedDraw()
    }

    override fun draw(pass: RenderPass) {
        textRenderer.draw(pass)
    }

    override fun clear() {
        textRenderer.clear()
    }

    override fun close() {
        textRenderer.close()
        if (registered) {
            RendererManager.INSTANCE.unregister(this)
            registered = false
        }
    }

    private fun ensureRegistered() {
        if (!registered) {
            RendererManager.INSTANCE.register(this)
            registered = true
        }
    }

}
