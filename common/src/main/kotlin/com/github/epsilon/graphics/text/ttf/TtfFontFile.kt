package com.github.epsilon.graphics.text.ttf

import com.github.epsilon.assets.resources.ResourceLocationUtils
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import net.minecraft.resources.Identifier
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

/**
 * 缺字占位框（口字形）的尺寸比例：高度取 ascent 的 2/3，宽度与笔画由高度推导。
 */
private const val FALLBACK_HEIGHT_RATIO = 2.0f / 3.0f
private const val FALLBACK_WIDTH_RATIO = 0.72f
private const val FALLBACK_STROKE_RATIO = 1.0f / 8.0f
/**
 * 占位框位图四周外扩的像素数，保住墨迹边缘之外的一圈 SDF 过渡带。
 */
private const val FALLBACK_PADDING = 2

class TtfFontFile private constructor(
    private val fontData: ByteBuffer,
    totalHeight: Int,
    private val padding: Int,
    debugName: String
) {

    private val fontInfo: STBTTFontinfo = STBTTFontinfo.create()

    val scale: Float

    val pixelAscent: Int

    val fontHeight: Int

    init {
        if (!STBTruetype.stbtt_InitFont(fontInfo, fontData)) {
            MemoryUtil.memFree(fontData)
            throw IllegalStateException("STB TrueType failed to load ttf font: $debugName")
        }

        scale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, (totalHeight - padding * 2).toFloat())

        var pixelAscentValue = 0
        var fontHeightValue = 0
        val stack = MemoryStack.stackPush()
        try {
            val ascentBuf = stack.callocInt(1)
            val descentBuf = stack.callocInt(1)
            val lineGapBuf = stack.mallocInt(1)
            STBTruetype.stbtt_GetFontVMetrics(fontInfo, ascentBuf, descentBuf, lineGapBuf)

            val ascent = ascentBuf.get()
            val descent = descentBuf.get()
            val lineGap = lineGapBuf.get()

            pixelAscentValue = (ascent * scale).toInt()
            fontHeightValue = ((ascent - descent + lineGap) * scale).toInt()
        } finally {
            stack.close()
        }

        pixelAscent = pixelAscentValue
        fontHeight = fontHeightValue

//        this.fontHeight = totalHeight - padding * 2;
    }

    constructor(ttfFile: Identifier, totalHeight: Int, padding: Int) :
        this(ResourceLocationUtils.loadResource(ttfFile), totalHeight, padding, ttfFile.toString())

    constructor(ttfFile: Path, totalHeight: Int, padding: Int) :
        this(loadFontFile(ttfFile), totalHeight, padding, ttfFile.toString())

    @Synchronized
    fun generateGlyph(ch: Char): TtfGlyph {
        return generateGlyph(ch.code)
    }

    @Synchronized
    fun generateGlyph(codepoint: Int): TtfGlyph {
        val glyphIndex = STBTruetype.stbtt_FindGlyphIndex(fontInfo, codepoint)

        // (byte) 128 溢出为 -128，于是 pixelDistScale 为负：墨迹落在 128 以下（暗），外部落在 128 以上（亮），
        // 与 ttf_font_* 着色器的 1 - r 解释一致。改动这里的符号会让所有文字反相。
        val onEdgeValue: Byte = 128.toByte()
        val pixelDistScale = onEdgeValue.toFloat() / padding

        val stack = MemoryStack.stackPush()
        try {
            val width = stack.callocInt(1)
            val height = stack.callocInt(1)
            val xOff = stack.callocInt(1)
            val yOff = stack.callocInt(1)

            val sdfPixels = STBTruetype.stbtt_GetGlyphSDF(
                fontInfo,
                scale,
                glyphIndex,
                padding,
                onEdgeValue,
                pixelDistScale,
                width,
                height,
                xOff,
                yOff
            )

            val glyphWidth = width.get(0)
            val glyphHeight = height.get(0)
            val alphaPixels = MemoryUtil.memCalloc(maxOf(1, glyphWidth * glyphHeight))
            val bitmapStack = MemoryStack.stackPush()
            try {
                val bitmapWidth = bitmapStack.callocInt(1)
                val bitmapHeight = bitmapStack.callocInt(1)
                val bitmapX = bitmapStack.callocInt(1)
                val bitmapY = bitmapStack.callocInt(1)
                val bitmap = STBTruetype.stbtt_GetGlyphBitmap(
                    fontInfo, scale, scale, glyphIndex,
                    bitmapWidth, bitmapHeight, bitmapX, bitmapY
                )
                try {
                    if (bitmap != null && glyphWidth > 0 && glyphHeight > 0) {
                        val copyWidth = minOf(bitmapWidth.get(0), glyphWidth)
                        val copyHeight = minOf(bitmapHeight.get(0), glyphHeight)
                        val dstX = bitmapX.get(0) - xOff.get(0)
                        val dstY = bitmapY.get(0) - yOff.get(0)
                        for (row in 0 until copyHeight) {
                            val targetY = row + dstY
                            if (targetY < 0 || targetY >= glyphHeight) continue
                            for (column in 0 until copyWidth) {
                                val targetX = column + dstX
                                if (targetX >= 0 && targetX < glyphWidth) {
                                    alphaPixels.put(
                                        targetY * glyphWidth + targetX,
                                        bitmap.get(row * bitmapWidth.get(0) + column)
                                    )
                                }
                            }
                        }
                    }
                } finally {
                    if (bitmap != null) {
                        STBTruetype.stbtt_FreeBitmap(bitmap)
                    }
                }
            } finally {
                bitmapStack.close()
            }

            val advance = stack.callocInt(1)
            val lsb = stack.callocInt(1)
            STBTruetype.stbtt_GetGlyphHMetrics(fontInfo, glyphIndex, advance, lsb)

            return TtfGlyph(
                sdfPixels,
                alphaPixels,
                glyphWidth,
                glyphHeight,
                xOff.get(),
                yOff.get(),
                (advance.get() * scale).toInt()
            )
        } finally {
            stack.close()
        }
    }

    /**
     * 生成缺字占位字形：一个“口”字形方框，供字形尚未上传或字体缺失该字形时渲染。
     *
     * 尺寸由字体 ascent 推导，与多数字体自带 `.notdef` 方框接近；位图四周外扩
     * `FALLBACK_PADDING` 像素，让墨迹边缘外仍有 SDF 过渡带。
     *
     * SDF 极性必须与 [generateGlyph] 保持一致：那里的 `onEdgeValue` 是 byte 128
     * （即 -128），使 `pixelDistScale` 为负，于是墨迹（内部距离为正）落在 128 以下、
     * 外部落在 128 以上；着色器按 `1 - r` 解释该纹理，墨迹才是可见部分。
     */
    fun generateFallbackGlyph(): TtfGlyph {
        val ascent = maxOf(pixelAscent, 1)
        val boxHeight = maxOf(3, (ascent * FALLBACK_HEIGHT_RATIO).roundToInt())
        val boxWidth = maxOf(3, (boxHeight * FALLBACK_WIDTH_RATIO).roundToInt())
        // 笔画不能太细（缩放后会消失），也不能填满方框（环会退化成实心块）。
        val maxStroke = maxOf(1, (minOf(boxWidth, boxHeight) - 1) / 2)
        val stroke = ((boxHeight * FALLBACK_STROKE_RATIO).roundToInt().toLong()).coerceIn(((1).toLong()).toLong(), ((maxStroke).toLong()).toLong()).toInt().toInt()

        val bitmapWidth = boxWidth + FALLBACK_PADDING * 2
        val bitmapHeight = boxHeight + FALLBACK_PADDING * 2
        val xOffset = -FALLBACK_PADDING
        val yOffset = -boxHeight - FALLBACK_PADDING

        val sdfPixels = MemoryUtil.memAlloc(bitmapWidth * bitmapHeight)
        val alphaPixels = MemoryUtil.memCalloc(bitmapWidth * bitmapHeight)

        val halfWidth = boxWidth / 2.0
        val halfHeight = boxHeight / 2.0
        val centerX = halfWidth
        val centerY = -halfHeight
        val innerHalfWidth = maxOf(halfWidth - stroke, 0.0)
        val innerHalfHeight = maxOf(halfHeight - stroke, 0.0)
        val onEdgeValue = 128.0
        val pixelDistScale = -onEdgeValue / padding

        for (row in 0 until bitmapHeight) {
            val localY = yOffset + row + 0.5
            for (column in 0 until bitmapWidth) {
                val localX = xOffset + column + 0.5
                // ringDistance 是标准 SDF 约定（墨迹内为负），取负后才是 stb 在墨迹内为正的 min_dist。
                val distance = ringDistance(
                    localX - centerX, localY - centerY,
                    halfWidth, halfHeight, innerHalfWidth, innerHalfHeight
                )
                val index = row * bitmapWidth + column
                sdfPixels.put(index, (onEdgeValue + pixelDistScale * -distance).coerceIn(0.0, 255.0).toInt().toByte())
                alphaPixels.put(index, ((0.5 - distance).coerceIn(0.0, 1.0) * 255.0).toInt().toByte())
            }
        }

        // advance 只作兜底：TtfFontLoader 对外会换成目标字符自身的步进宽度。
        return TtfGlyph(sdfPixels, alphaPixels, bitmapWidth, bitmapHeight, xOffset, yOffset, boxWidth + stroke)
    }

    @Synchronized
    fun getAdvance(ch: Char): Int {
        return getAdvance(ch.code)
    }

    @Synchronized
    fun getAdvance(codepoint: Int): Int {
        val glyphIndex = STBTruetype.stbtt_FindGlyphIndex(fontInfo, codepoint)

        val stack = MemoryStack.stackPush()
        try {
            val advance = stack.callocInt(1)
            val lsb = stack.callocInt(1)
            STBTruetype.stbtt_GetGlyphHMetrics(fontInfo, glyphIndex, advance, lsb)
            return (advance.get() * scale).toInt()
        } finally {
            stack.close()
        }
    }

    @Synchronized
    fun getVisualHeight(sample: String?): Float {
        if (sample == null || sample.isEmpty()) {
            return fontHeight.toFloat()
        }

        val stack = MemoryStack.stackPush()
        try {
            val x0 = stack.mallocInt(1)
            val y0 = stack.mallocInt(1)
            val x1 = stack.mallocInt(1)
            val y1 = stack.mallocInt(1)
            var minY = Int.MAX_VALUE
            var maxY = Int.MIN_VALUE

            var i = 0
            while (i < sample.length) {
                val codepoint = Character.codePointAt(sample, i)
                i += Character.charCount(codepoint)
                if (Character.isWhitespace(codepoint)) {
                    continue
                }

                val glyphIndex = STBTruetype.stbtt_FindGlyphIndex(fontInfo, codepoint)
                if (glyphIndex == 0) {
                    continue
                }

                STBTruetype.stbtt_GetGlyphBitmapBox(fontInfo, glyphIndex, scale, scale, x0, y0, x1, y1)
                if (y1.get(0) <= y0.get(0)) {
                    continue
                }

                minY = minOf(minY, y0.get(0))
                maxY = maxOf(maxY, y1.get(0))
            }

            return if (maxY > minY) (maxY - minY).toFloat() else fontHeight.toFloat()
        } finally {
            stack.close()
        }
    }

    fun destroy() {
        MemoryUtil.memFree(fontData)
    }

}

private fun loadFontFile(path: Path): ByteBuffer {
    try {
        val bytes = Files.readAllBytes(path)
        val buffer = MemoryUtil.memAlloc(bytes.size)
        buffer.put(bytes)
        buffer.flip()
        return buffer
    } catch (e: IOException) {
        throw RuntimeException("Failed to read font file: $path", e)
    }
}

/**
 * 矩形环（“口”字框）的有符号距离，墨迹内为负。
 */
private fun ringDistance(
    x: Double, y: Double, halfWidth: Double, halfHeight: Double,
    innerHalfWidth: Double, innerHalfHeight: Double
): Double {
    return maxOf(boxDistance(x, y, halfWidth, halfHeight), -boxDistance(x, y, innerHalfWidth, innerHalfHeight))
}

/**
 * 矩形有符号距离，内部为负。
 */
private fun boxDistance(x: Double, y: Double, halfWidth: Double, halfHeight: Double): Double {
    val qx = abs(x) - halfWidth
    val qy = abs(y) - halfHeight
    return hypot(maxOf(qx, 0.0), maxOf(qy, 0.0)) + minOf(maxOf(qx, qy), 0.0)
}
