package com.github.epsilon.graphics.text.ttf

import com.github.epsilon.graphics.LuminRenderPipelines
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.LuminTexture
import com.github.epsilon.graphics.buffer.BufferUtils
import com.github.epsilon.graphics.buffer.LuminRingBuffer
import com.github.epsilon.graphics.text.GlyphDescriptor
import com.github.epsilon.graphics.text.ITextRenderer
import com.github.epsilon.graphics.text.TextGlitchEffect
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.utils.math.toRadians
import com.github.epsilon.utils.render.ScissorUtils
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.minecraft.client.renderer.DynamicGpuDataStorage
import net.minecraft.util.ARGB
import org.joml.Vector4fc
import org.lwjgl.system.MemoryUtil
import java.awt.Color
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

private const val DEFAULT_SCALE = 0.35f
private const val SPACING = 0f
private const val STANDARD_STRIDE = 24
private const val FONT_BLUR_STRIDE = 40
private const val FONT_BLUR_UNIFORM_SIZE = 16
private const val MAX_FONT_BLUR_RADIUS = 20.0f
private const val FONT_BLUR_EXTRA_PADDING = 2.0f
private const val LAYOUT_FLOATS_PER_GLYPH = 10
private const val LAYOUT_CACHE_LIMIT = 256
private const val WIDTH_CACHE_LIMIT = 256
private const val SPACE_WIDTH = 3.0f

class TtfTextRenderer : ITextRenderer {

    private val bufferSize: Long

    enum class Mode {
        STANDARD,
        FONT_BLUR,
        GLITCH
    }

    private val mode: Mode
    private val stride: Int
    private val glyphBytes: Long

    private val batches: MutableMap<BatchKey, Batch> = LinkedHashMap()
    // 缓存与 scale 无关的布局数据，绘制时只做平移和缩放。
    private val layoutCache: MutableMap<LayoutKey, TextLayout> = object : LinkedHashMap<LayoutKey, TextLayout>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LayoutKey, TextLayout>): Boolean {
            return size > LAYOUT_CACHE_LIMIT
        }
    }
    // 宽度测量调用很密集，单独缓存避免为了布局缓存而请求/上传字形。
    private val widthCache: MutableMap<LayoutKey, Float> = object : LinkedHashMap<LayoutKey, Float>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LayoutKey, Float>): Boolean {
            return size > WIDTH_CACHE_LIMIT
        }
    }

    private var scissorEnabled = false
    private var scissorX = 0
    private var scissorY = 0
    private var scissorW = 0
    private var scissorH = 0
    private var sharedDynamicUniforms: GpuBufferSlice? = null
    private var sharedMaxIndexCount = 0

    constructor(bufferSize: Long) : this(bufferSize, Mode.STANDARD)

    constructor(bufferSize: Long, fontBlur: Boolean) :
        this(bufferSize, if (fontBlur) Mode.FONT_BLUR else Mode.STANDARD)

    constructor(bufferSize: Long, mode: Mode) {
        this.bufferSize = bufferSize
        this.mode = Objects.requireNonNull(mode, "mode")
        this.stride = if (mode == Mode.STANDARD) STANDARD_STRIDE else FONT_BLUR_STRIDE
        this.glyphBytes = this.stride * 4L
    }

    constructor() : this(64L * 1024)

    override fun addText(text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader) {
        if (text.isEmpty() || color.alpha == 0) return

        fontLoader.prepareChars(text)
        emitLayout(layoutFor(text, fontLoader), x, y, scale, ARGB.toABGR(color.rgb), 0.0f)
    }

    override fun addBlurredText(
        text: String, x: Float, y: Float, scale: Float, color: Color,
        blurRadius: Float, fontLoader: TtfFontLoader
    ) {
        if (mode != Mode.FONT_BLUR) {
            throw IllegalStateException("Font blur text requires TextRenderer.createFontBlur().")
        }
        if (text.isEmpty() || color.alpha == 0 || !blurRadius.isFinite() || blurRadius <= 0.0f) return

        fontLoader.prepareChars(text)
        val clampedBlur = (blurRadius).coerceIn(0.0f, MAX_FONT_BLUR_RADIUS)
        emitLayout(layoutFor(text, fontLoader), x, y, scale, ARGB.toABGR(color.rgb), clampedBlur)
    }

    override fun addGlitchText(
        text: String, x: Float, y: Float, scale: Float, color: Color,
        effect: TextGlitchEffect?, fontLoader: TtfFontLoader
    ) {
        if (mode != Mode.GLITCH) {
            throw IllegalStateException("Glitch text requires TextRenderer.createGlitch().")
        }
        if (text.isEmpty() || color.alpha == 0 || effect == null) return

        fontLoader.prepareChars(text)
        emitGlitchLayout(layoutFor(text, fontLoader), x, y, scale, ARGB.toABGR(color.rgb), effect)
    }

    override fun addRotatedText(
        text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader,
        originX: Float, originY: Float, rotationDegrees: Float
    ) {
        if (text.isEmpty() || color.alpha == 0) return

        fontLoader.prepareChars(text)
        emitRotatedLayout(
            layoutFor(text, fontLoader), x, y, scale, ARGB.toABGR(color.rgb),
            originX, originY, rotationDegrees
        )
    }

    override fun addGradientText(
        text: String, x: Float, y: Float, scale: Float, startColor: Color, endColor: Color,
        fontLoader: TtfFontLoader
    ) {
        if (text.isEmpty() || startColor.alpha == 0 && endColor.alpha == 0) return

        fontLoader.prepareChars(text)
        val layout = layoutFor(text, fontLoader)
        val totalWidth = if (layout.complete) layout.width else baseWidth(text, fontLoader)
        emitGradientLayout(layout, x, y, scale, totalWidth, startColor.rgb, endColor.rgb)
    }

    private fun layoutFor(text: String, fontLoader: TtfFontLoader): TextLayout {
        val revision = fontLoader.getGlyphRevision()
        val atlasRevision = fontLoader.getAtlasRevision()
        val key = LayoutKey(fontLoader, text, fontLoader.getRenderScale())
        val cached = layoutCache[key]
        // 完整布局可跨无关 glyph 加载复用；未完成布局等待 glyphRevision 变化后重建。
        if (cached != null && cached.atlasRevision == atlasRevision && (cached.complete || cached.glyphRevision == revision)) {
            return cached
        }

        val layout = buildLayout(text, fontLoader, revision, atlasRevision)
        layoutCache[key] = layout
        return layout
    }

    private fun buildLayout(text: String, fontLoader: TtfFontLoader, revision: Long, atlasRevision: Long): TextLayout {
        // 坐标统一存 DEFAULT_SCALE 下的基础值，emit 时再乘调用方传入的 scale。
        val runBuilders = LinkedHashMap<TtfGlyphAtlas, LayoutRunBuilder>()
        var xOffset = 0.0f
        var yOffset = 0.0f
        var maxLine = 0.0f
        val fontScale = fontLoader.getRenderScale()
        val scaledFont = DEFAULT_SCALE * fontScale
        val ascent = fontLoader.fontFile.pixelAscent * scaledFont
        val lineHeight = fontLoader.fontFile.fontHeight * scaledFont
        val spaceWidth = SPACE_WIDTH * fontScale
        var complete = true
        var glyphCount = 0

        var i = 0
        while (i < text.length) {
            val codepoint = Character.codePointAt(text, i)
            i += Character.charCount(codepoint)
            if (codepoint == ' '.code) {
                xOffset += spaceWidth
                continue
            }
            if (codepoint == '\n'.code) {
                maxLine = maxOf(maxLine, xOffset)
                xOffset = 0.0f
                yOffset += lineHeight
                continue
            }

            var glyph = fontLoader.getGlyph(codepoint)
            if (glyph == null) {
                // 字形尚未上传（或字体缺失该字形）时用口字形占位框顶上；占位字形同样写入 atlas，
                // complete 保持 false，真实字形上传推进 glyphRevision 后会重建布局换回真实字形。
                complete = false
                glyph = if (hasNoInk(codepoint)) null else fontLoader.getFallbackGlyph(codepoint)
            }
            if (glyph == null) {
                continue
            }
            val descriptor: GlyphDescriptor = glyph

            val x1 = xOffset + descriptor.xOffset * scaledFont
            val x2 = x1 + descriptor.width * scaledFont
            val y1 = yOffset + ascent + descriptor.yOffset * scaledFont
            val y2 = y1 + descriptor.height * scaledFont
            val advance = descriptor.advance * scaledFont + SPACING

            runBuilders.getOrPut(descriptor.atlas) { LayoutRunBuilder(descriptor.atlas) }
                .add(x1, y1, x2, y2, descriptor.uv, xOffset, xOffset + descriptor.advance * scaledFont)

            xOffset += advance
            glyphCount++
        }

        maxLine = maxOf(maxLine, xOffset)

        val runs = ArrayList<LayoutRun>(runBuilders.size)
        for (builder in runBuilders.values) {
            runs.add(builder.build())
        }

        return TextLayout(runs.toTypedArray(), glyphCount, maxLine, complete, revision, atlasRevision)
    }

    private fun emitLayout(layout: TextLayout, x: Float, y: Float, scale: Float, argb: Int, blurRadius: Float) {
        if (layout.glyphCount == 0) return

        for (run in layout.runs) {
            val batch = batchFor(run.atlas, blurRadius)
            var p = batch.beginWrite(run.glyphCount)
            val data = run.data
            for (i in 0 until run.glyphCount) {
                if (mode == Mode.FONT_BLUR) {
                    writeFontBlurGlyph(p, x, y, scale, data, i * LAYOUT_FLOATS_PER_GLYPH, argb, blurRadius)
                } else {
                    writeGlyph(p, x, y, scale, data, i * LAYOUT_FLOATS_PER_GLYPH, argb, argb)
                }
                p += glyphBytes
            }
        }
    }

    private fun emitGlitchLayout(layout: TextLayout, x: Float, y: Float, scale: Float, argb: Int, effect: TextGlitchEffect) {
        if (layout.glyphCount == 0) return

        val padding = effect.requiredPadding()
        for (run in layout.runs) {
            val batch = batchFor(run.atlas, 0.0f, effect)
            var p = batch.beginWrite(run.glyphCount)
            val data = run.data
            for (i in 0 until run.glyphCount) {
                writeEffectGlyph(p, x, y, scale, data, i * LAYOUT_FLOATS_PER_GLYPH, argb, padding)
                p += glyphBytes
            }
        }
    }

    private fun emitRotatedLayout(
        layout: TextLayout, x: Float, y: Float, scale: Float, argb: Int,
        originX: Float, originY: Float, rotationDegrees: Float
    ) {
        if (layout.glyphCount == 0) return

        val radians = (rotationDegrees.toDouble()).toRadians().toFloat()
        val cos = cos(radians.toDouble()).toFloat()
        val sin = sin(radians.toDouble()).toFloat()
        for (run in layout.runs) {
            val batch = batchFor(run.atlas, 0.0f)
            var p = batch.beginWrite(run.glyphCount)
            val data = run.data
            for (i in 0 until run.glyphCount) {
                writeRotatedGlyph(
                    p, x, y, scale, data, i * LAYOUT_FLOATS_PER_GLYPH, argb, argb,
                    originX, originY, cos, sin
                )
                p += glyphBytes
            }
        }
    }

    private fun emitGradientLayout(
        layout: TextLayout, x: Float, y: Float, scale: Float, width: Float,
        startArgb: Int, endArgb: Int
    ) {
        if (layout.glyphCount == 0) return

        val totalWidth = maxOf(width, 1.0f)
        for (run in layout.runs) {
            val batch = batchFor(run.atlas, 0.0f)
            var p = batch.beginWrite(run.glyphCount)
            val data = run.data
            for (i in 0 until run.glyphCount) {
                val base = i * LAYOUT_FLOATS_PER_GLYPH
                val leftArgb = ARGB.toABGR(ARGB.srgbLerp(clamp01(data[base + 8] / totalWidth), startArgb, endArgb))
                val rightArgb = ARGB.toABGR(ARGB.srgbLerp(clamp01(data[base + 9] / totalWidth), startArgb, endArgb))
                writeGlyph(p, x, y, scale, data, base, leftArgb, rightArgb)
                p += glyphBytes
            }
        }
    }

    private fun batchFor(atlas: TtfGlyphAtlas, blurRadius: Float): Batch {
        return batchFor(atlas, blurRadius, null)
    }

    private fun batchFor(atlas: TtfGlyphAtlas, blurRadius: Float, glitchEffect: TextGlitchEffect?): Batch {
        val key = BatchKey(atlas, blurRadius, glitchEffect)
        return batches.getOrPut(key) {
            Batch(
                LuminRingBuffer(bufferSize, GpuBuffer.USAGE_VERTEX), atlas, blurRadius, glitchEffect
            )
        }
    }

    private fun baseWidth(text: String, fontLoader: TtfFontLoader): Float {
        val key = LayoutKey(fontLoader, text, fontLoader.getRenderScale())
        val cached = widthCache[key]
        if (cached != null) {
            return cached
        }

        var maxLine = 0.0f
        var currentLine = 0.0f
        val fontScale = fontLoader.getRenderScale()
        val scaledFont = DEFAULT_SCALE * fontScale
        val spaceWidth = SPACE_WIDTH * fontScale
        var i = 0
        while (i < text.length) {
            val codepoint = Character.codePointAt(text, i)
            i += Character.charCount(codepoint)
            if (codepoint == ' '.code) {
                currentLine += spaceWidth
            } else if (codepoint == '\n'.code) {
                maxLine = maxOf(maxLine, currentLine)
                currentLine = 0.0f
            } else {
                currentLine += fontLoader.getAdvance(codepoint) * scaledFont + SPACING
            }
        }

        val width = maxOf(maxLine, currentLine)
        widthCache[key] = width
        return width
    }

    override fun draw() {
        if (batches.isEmpty()) return

        LuminRenderSystem.applyOrthoProjection()

        val colorView = LuminRenderSystem.resolveColorView()
        val depthView = LuminRenderSystem.resolveDepthView()
        if (colorView == null) return
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return

        val maxIndexCount = prepareTextBatches()
        if (maxIndexCount == 0) return

        val dynamicUniforms = LuminRenderSystem.writeDefaultGuiTransform()
        val ibo = LuminRenderSystem.getQuadIndexBuffer(maxIndexCount)
        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { "Lumin TTF Draws" },
            colorView, Optional.empty<Vector4fc>(),
            depthView, OptionalDouble.empty()
        ).use { pass ->
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline()))
            if (scissorEnabled) {
                ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)
            }

            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform("DynamicTransforms", dynamicUniforms)
            pass.setIndexBuffer(ibo, LuminRenderSystem.getQuadIndexType())

            drawPrepared(pass)
        }
    }

    override fun prepareSharedDraw(): Boolean {
        sharedDynamicUniforms = null
        sharedMaxIndexCount = 0
        if (batches.isEmpty()) return false
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return false

        sharedMaxIndexCount = prepareTextBatches()
        if (sharedMaxIndexCount == 0) return false

        LuminRenderSystem.getQuadIndexBuffer(sharedMaxIndexCount)
        sharedDynamicUniforms = LuminRenderSystem.writeDefaultGuiTransform()
        return sharedDynamicUniforms != null
    }

    override fun draw(pass: RenderPass) {
        if (sharedDynamicUniforms == null || sharedMaxIndexCount == 0) return

        val ibo = LuminRenderSystem.getQuadIndexBuffer(sharedMaxIndexCount)
        pass.setIndexBuffer(ibo, LuminRenderSystem.getQuadIndexType())
        pass.setUniform("DynamicTransforms", sharedDynamicUniforms!!)
        drawPrepared(pass)
    }

    private fun prepareTextBatches(): Int {
        var maxIndexCount = 0
        for (batch in batches.values) {
            if (batch.offsetInAtlas == 0L) continue
            if (batch.buffer.isMapped()) {
                batch.buffer.unmap()
                batch.mappedAddress = 0L
            }

            val vertexCount = (batch.offsetInAtlas / stride).toInt()
            maxIndexCount = maxOf(maxIndexCount, vertexCount / 4 * 6)
            if (mode == Mode.FONT_BLUR) {
                batch.blurUniforms = LuminRenderSystem.writeDynamicUniform(
                    "font_blur_uniforms",
                    "Lumin Font Blur UBO",
                    FONT_BLUR_UNIFORM_SIZE,
                    16,
                    FontBlurUniforms(batch.blurRadius)
                )
            } else if (mode == Mode.GLITCH) {
                batch.glitchUniforms = LuminRenderSystem.writeDynamicUniform(
                    "font_glitch_uniforms",
                    "Lumin Font Glitch UBO",
                    64,
                    16,
                    GlitchUniforms(batch.glitchEffect!!, glitchTime())
                )
            }
        }
        return maxIndexCount
    }

    private fun drawPrepared(pass: RenderPass) {
        if (scissorEnabled) {
            if (!ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)) {
                return
            }
        } else {
            pass.disableScissor()
        }

        // 不同 atlas 共享同一字体 pipeline，在同一个 pass 内只切换纹理并连续 draw。
        for (batch in batches.values) {
            val atlas = batch.atlas

            if (batch.offsetInAtlas == 0L) continue

            val vertexCount = (batch.offsetInAtlas / stride).toInt()
            val indexCount = vertexCount / 4 * 6

            pass.setVertexBuffer(0, batch.buffer.getGpuBuffer().slice())
            val fontTexture: LuminTexture = if (mode == Mode.STANDARD) atlas.getTexture() else atlas.getAlphaTexture()
            pass.setUniform("Sampler0", fontTexture.textureView, fontTexture.sampler)
            if (mode == Mode.FONT_BLUR) {
                pass.setUniform("FontBlurUniforms", batch.blurUniforms!!)
            } else if (mode == Mode.GLITCH) {
                pass.setUniform("GlitchData", batch.glitchUniforms!!)
            }

            pass.drawIndexed(indexCount, 1, 0, 0, 0)
        }
    }

    override fun clear() {
        for (batch in batches.values) {
            if (batch.offsetInAtlas > 0) {
                if (batch.buffer.isMapped()) {
                    batch.buffer.unmap()
                    batch.mappedAddress = 0L
                }
                batch.buffer.rotate()
            }
            batch.offsetInAtlas = 0
            batch.blurUniforms = null
            batch.glitchUniforms = null
        }
        sharedDynamicUniforms = null
        sharedMaxIndexCount = 0
    }

    override fun close() {
        clear()
        for (batch in batches.values) {
            batch.buffer.close()
        }
        batches.clear()
        layoutCache.clear()
        widthCache.clear()
    }

    override fun getHeight(scale: Float, fontLoader: TtfFontLoader): Float {
        return fontLoader.fontFile.fontHeight * DEFAULT_SCALE * fontLoader.getRenderScale() * scale
    }

    override fun getWidth(text: String, scale: Float, fontLoader: TtfFontLoader): Float {
        if (text.isEmpty()) return 0.0f
        return baseWidth(text, fontLoader) * scale
    }

    override fun setScissor(x: Int, y: Int, width: Int, height: Int) {
        val scissor = ScissorUtils.clampFramebufferScissor(x, y, width, height)
        scissorEnabled = true
        scissorX = scissor.x
        scissorY = scissor.y
        scissorW = scissor.width
        scissorH = scissor.height
    }

    override fun clearScissor() {
        scissorEnabled = false
    }

    private fun pipeline(): RenderPipeline {
        return if (mode == Mode.FONT_BLUR) {
            LuminRenderPipelines.TTF_FONT_BLUR
        } else if (mode == Mode.GLITCH) {
            LuminRenderPipelines.TTF_FONT_GLITCH
        } else {
            if (ClientSetting.fontAntiAliasing) {
                LuminRenderPipelines.TTF_FONT_AA
            } else {
                LuminRenderPipelines.TTF_FONT_NO_AA
            }
        }
    }

    private inner class Batch(
        val buffer: LuminRingBuffer,
        val atlas: TtfGlyphAtlas,
        val blurRadius: Float,
        val glitchEffect: TextGlitchEffect?
    ) {
        var offsetInAtlas = 0L
        var mappedAddress = 0L
        var blurUniforms: GpuBufferSlice? = null
        var glitchUniforms: GpuBufferSlice? = null

        fun beginWrite(glyphCount: Int): Long {
            val start = offsetInAtlas
            val requiredBytes = start + glyphCount * glyphBytes
            buffer.ensureCapacity(requiredBytes)

            // 同一 atlas run 只映射一次，后续 glyph 直接顺序写入 mapped memory。
            if (!buffer.isMapped()) {
                buffer.tryMap()
            }
            // 扩容会解除旧映射并创建新的映射，必须在扩容后重新获取地址。
            mappedAddress = MemoryUtil.memAddress(buffer.getMappedBuffer())

            offsetInAtlas = requiredBytes
            return mappedAddress + start
        }
    }

    private data class BatchKey(val atlas: TtfGlyphAtlas, val blurRadius: Float, val glitchEffect: TextGlitchEffect?)

    private data class FontBlurUniforms(val blurRadius: Float) : DynamicGpuDataStorage.DynamicGpuData {
        override fun write(buffer: ByteBuffer) {
            Std140Builder.intoBuffer(buffer).putVec4(blurRadius, 0.0f, 0.0f, 0.0f)
        }
    }

    private data class GlitchUniforms(val effect: TextGlitchEffect, val time: Float) : DynamicGpuDataStorage.DynamicGpuData {
        override fun write(buffer: ByteBuffer) {
            val color = effect.neonColor
            Std140Builder.intoBuffer(buffer)
                .putVec4(effect.chromaticX, effect.chromaticY, effect.glowRadius, effect.glowIntensity)
                .putVec4(effect.sliceHeight, effect.sliceAmount, effect.glitchStrength, effect.scanlineStrength)
                .putVec4(color.red / 255.0f, color.green / 255.0f, color.blue / 255.0f, effect.noiseStrength)
                .putVec4(time, 0.0f, 0.0f, 0.0f)
        }
    }

    private data class LayoutKey(val fontLoader: TtfFontLoader, val text: String, val renderScale: Float)

    private class TextLayout(
        val runs: Array<LayoutRun>,
        val glyphCount: Int,
        val width: Float,
        val complete: Boolean,
        val glyphRevision: Long,
        val atlasRevision: Long
    )

    private class LayoutRun(val atlas: TtfGlyphAtlas, val data: FloatArray, val glyphCount: Int)

    private class LayoutRunBuilder(private val atlas: TtfGlyphAtlas) {

        private var data = FloatArray(LAYOUT_FLOATS_PER_GLYPH * 16)
        private var glyphCount = 0

        fun add(
            x1: Float, y1: Float, x2: Float, y2: Float, uv: TtfGlyphAtlas.GlyphUV,
            gradientLeft: Float, gradientRight: Float
        ) {
            ensureCapacity(glyphCount + 1)
            val base = glyphCount * LAYOUT_FLOATS_PER_GLYPH
            data[base] = x1
            data[base + 1] = y1
            data[base + 2] = x2
            data[base + 3] = y2
            data[base + 4] = uv.u0
            data[base + 5] = uv.v0
            data[base + 6] = uv.u1
            data[base + 7] = uv.v1
            data[base + 8] = gradientLeft
            data[base + 9] = gradientRight
            glyphCount++
        }

        private fun ensureCapacity(targetGlyphCount: Int) {
            val required = targetGlyphCount * LAYOUT_FLOATS_PER_GLYPH
            if (required <= data.size) {
                return
            }
            data = data.copyOf(maxOf(required, data.size * 2))
        }

        fun build(): LayoutRun {
            val used = glyphCount * LAYOUT_FLOATS_PER_GLYPH
            return LayoutRun(atlas, if (used == data.size) data else data.copyOf(used), glyphCount)
        }
    }

}

/**
 * 空白、控制、格式与代理码位本身没有墨迹，缺字时不画占位框。
 */
private fun hasNoInk(codepoint: Int): Boolean {
    val type = Character.getType(codepoint)
    return Character.isWhitespace(codepoint) ||
            type == Character.CONTROL.toInt() ||
            type == Character.FORMAT.toInt() ||
            type == Character.SURROGATE.toInt()
}

private fun writeGlyph(p: Long, x: Float, y: Float, scale: Float, data: FloatArray, base: Int, leftArgb: Int, rightArgb: Int) {
    val x1 = x + data[base] * scale
    val y1 = y + data[base + 1] * scale
    val x2 = x + data[base + 2] * scale
    val y2 = y + data[base + 3] * scale
    val u0 = data[base + 4]
    val v0 = data[base + 5]
    val u1 = data[base + 6]
    val v1 = data[base + 7]

    BufferUtils.writeUvRectToAddr(p, x1, y1, u0, v0, leftArgb)
    BufferUtils.writeUvRectToAddr(p + STANDARD_STRIDE, x1, y2, u0, v1, leftArgb)
    BufferUtils.writeUvRectToAddr(p + STANDARD_STRIDE * 2L, x2, y2, u1, v1, rightArgb)
    BufferUtils.writeUvRectToAddr(p + STANDARD_STRIDE * 3L, x2, y1, u1, v0, rightArgb)
}

private fun writeFontBlurGlyph(
    p: Long, x: Float, y: Float, scale: Float, data: FloatArray, base: Int,
    argb: Int, blurRadius: Float
) {
    writeEffectGlyph(p, x, y, scale, data, base, argb, blurRadius + FONT_BLUR_EXTRA_PADDING)
}

private fun writeEffectGlyph(
    p: Long, x: Float, y: Float, scale: Float, data: FloatArray, base: Int,
    argb: Int, padding: Float
) {
    val x1 = x + data[base] * scale
    val y1 = y + data[base + 1] * scale
    val x2 = x + data[base + 2] * scale
    val y2 = y + data[base + 3] * scale
    val u0 = data[base + 4]
    val v0 = data[base + 5]
    val u1 = data[base + 6]
    val v1 = data[base + 7]
    val uPadding = padding * (u1 - u0) / maxOf(x2 - x1, 0.0001f)
    val vPadding = padding * (v1 - v0) / maxOf(y2 - y1, 0.0001f)

    writeFontBlurVertex(
        p, x1 - padding, y1 - padding, u0 - uPadding, v0 - vPadding,
        argb, u0, v0, u1, v1
    )
    writeFontBlurVertex(
        p + FONT_BLUR_STRIDE, x1 - padding, y2 + padding, u0 - uPadding, v1 + vPadding,
        argb, u0, v0, u1, v1
    )
    writeFontBlurVertex(
        p + FONT_BLUR_STRIDE * 2L, x2 + padding, y2 + padding, u1 + uPadding, v1 + vPadding,
        argb, u0, v0, u1, v1
    )
    writeFontBlurVertex(
        p + FONT_BLUR_STRIDE * 3L, x2 + padding, y1 - padding, u1 + uPadding, v0 - vPadding,
        argb, u0, v0, u1, v1
    )
}

private fun writeFontBlurVertex(
    p: Long, x: Float, y: Float, u: Float, v: Float, color: Int,
    u0: Float, v0: Float, u1: Float, v1: Float
) {
    MemoryUtil.memPutFloat(p, x)
    MemoryUtil.memPutFloat(p + 4, y)
    MemoryUtil.memPutFloat(p + 8, 0.0f)
    MemoryUtil.memPutFloat(p + 12, u)
    MemoryUtil.memPutFloat(p + 16, v)
    MemoryUtil.memPutInt(p + 20, color)
    MemoryUtil.memPutFloat(p + 24, u0)
    MemoryUtil.memPutFloat(p + 28, v0)
    MemoryUtil.memPutFloat(p + 32, u1)
    MemoryUtil.memPutFloat(p + 36, v1)
}

private fun writeRotatedGlyph(
    p: Long, x: Float, y: Float, scale: Float, data: FloatArray, base: Int,
    leftArgb: Int, rightArgb: Int, originX: Float, originY: Float, cos: Float, sin: Float
) {
    val x1 = x + data[base] * scale
    val y1 = y + data[base + 1] * scale
    val x2 = x + data[base + 2] * scale
    val y2 = y + data[base + 3] * scale
    val u0 = data[base + 4]
    val v0 = data[base + 5]
    val u1 = data[base + 6]
    val v1 = data[base + 7]

    writeRotatedVertex(p, x1, y1, u0, v0, leftArgb, originX, originY, cos, sin)
    writeRotatedVertex(p + STANDARD_STRIDE, x1, y2, u0, v1, leftArgb, originX, originY, cos, sin)
    writeRotatedVertex(p + STANDARD_STRIDE * 2L, x2, y2, u1, v1, rightArgb, originX, originY, cos, sin)
    writeRotatedVertex(p + STANDARD_STRIDE * 3L, x2, y1, u1, v0, rightArgb, originX, originY, cos, sin)
}

private fun writeRotatedVertex(
    p: Long, x: Float, y: Float, u: Float, v: Float, color: Int,
    originX: Float, originY: Float, cos: Float, sin: Float
) {
    val dx = x - originX
    val dy = y - originY
    val rx = originX + dx * cos - dy * sin
    val ry = originY + dx * sin + dy * cos
    BufferUtils.writeUvRectToAddr(p, rx, ry, u, v, color)
}

private fun clamp01(value: Float): Float {
    return (value).coerceIn(0.0f, 1.0f)
}

private fun glitchTime(): Float {
    return (System.nanoTime() % 1_024_000_000_000L) / 1_000_000_000.0f
}
