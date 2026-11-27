package com.github.epsilon.graphics.text.ttf

import io.github.slmpc.msdfgen.Msdf
import io.github.slmpc.msdfgen.Shape
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/** STB 提取字体轮廓，由纯 Java msdfgen 生成 RGB 距离场。 */
internal object MsdfGlyphGenerator {
    // 与 ui_font_texture.glsl 的 EPSILON_MSDF_PIXEL_RANGE 保持一致；轮廓两侧各覆盖四个 atlas 像素。
    private const val PIXEL_RANGE = 8.0

    fun generate(
        fontInfo: STBTTFontinfo, glyphIndex: Int, scale: Float,
        width: Int, height: Int, xOffset: Int, yOffset: Int
    ): ByteBuffer? {
        if (width <= 0 || height <= 0) return null
        val vertices = STBTruetype.stbtt_GetGlyphShape(fontInfo, glyphIndex) ?: return null
        val shape = Shape()
        try {
            var contour: Shape.Contour? = null
            var x = 0.0
            var y = 0.0
            var startX = 0.0
            var startY = 0.0
            for (index in 0 until vertices.remaining()) {
                val vertex = vertices[index]
                val nextX = vertex.x().toDouble()
                val nextY = vertex.y().toDouble()
                when (vertex.type()) {
                    STBTruetype.STBTT_vmove -> {
                        contour?.line(x, y, startX, startY)
                        contour = shape.addContour()
                        startX = nextX
                        startY = nextY
                    }

                    STBTruetype.STBTT_vline -> checkNotNull(contour).line(x, y, nextX, nextY)
                    STBTruetype.STBTT_vcurve -> checkNotNull(contour).quadratic(
                        x, y, vertex.cx().toDouble(), vertex.cy().toDouble(), nextX, nextY
                    )

                    STBTruetype.STBTT_vcubic -> checkNotNull(contour).cubic(
                        x, y, vertex.cx().toDouble(), vertex.cy().toDouble(),
                        vertex.cx1().toDouble(), vertex.cy1().toDouble(), nextX, nextY
                    )

                    else -> error("Unsupported STB glyph vertex type: ${vertex.type()}")
                }
                x = nextX
                y = nextY
            }
            contour?.line(x, y, startX, startY)
        } finally {
            STBTruetype.stbtt_FreeShape(fontInfo, vertices)
        }
        return render(shape, width, height, scale.toDouble(), xOffset, yOffset)
    }

    fun generateFallback(boxWidth: Int, boxHeight: Int, stroke: Int, padding: Int): ByteBuffer {
        val shape = Shape()
        shape.addRectangle(0.0, 0.0, boxWidth.toDouble(), boxHeight.toDouble())
        shape.addRectangle(
            stroke.toDouble(), stroke.toDouble(),
            (boxWidth - stroke).toDouble(), (boxHeight - stroke).toDouble()
        )
        return render(shape, boxWidth + padding * 2, boxHeight + padding * 2, 1.0, -padding, -boxHeight - padding)
    }

    private fun render(
        shape: Shape, width: Int, height: Int, scale: Double, xOffset: Int, yOffset: Int
    ): ByteBuffer {
        val halfRange = PIXEL_RANGE * 0.5 / scale
        val transform = Msdf.Transform(scale, scale, -xOffset / scale, (yOffset + height) / scale, -halfRange, halfRange)
        val source = Msdf.generate(shape, width, height, transform)
        // 保持 atlas 上传缓冲的分配约定，由字形数据的持有方调用 memFree。
        val output = MemoryUtil.memAlloc(width * height * 4)
        try {
            for (row in 0 until height) {
                for (column in 0 until width) {
                    // Java msdfgen 从底行开始，atlas 与 STB 覆盖率位图均从顶行开始。
                    val sourceIndex = ((height - row - 1) * width + column) * 3
                    val targetIndex = (row * width + column) * 4
                    for (channel in 0..2) {
                        output.put(
                            targetIndex + channel,
                            (source[sourceIndex + channel].coerceIn(0f, 1f) * 255f).roundToInt().toByte()
                        )
                    }
                    output.put(targetIndex + 3, 0xFF.toByte())
                }
            }
            return output
        } catch (failure: Throwable) {
            MemoryUtil.memFree(output)
            throw failure
        }
    }

    private fun Shape.addRectangle(left: Double, bottom: Double, right: Double, top: Double) {
        val contour = addContour()
        contour.line(left, bottom, right, bottom)
        contour.line(right, bottom, right, top)
        contour.line(right, top, left, top)
        contour.line(left, top, left, bottom)
    }
}
