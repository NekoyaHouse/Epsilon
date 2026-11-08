package com.github.epsilon.graphics.text.ttf

import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MsdfGlyphTest {
    @Test
    fun distanceFieldMatchesStbCoverageAndKeepsMetrics() = withFont { font ->
        // 同时覆盖尖角、曲线、孔洞、复合轮廓、中文及补充平面缺字。
        for (codepoint in intArrayOf('A'.code, 'V'.code, 'M'.code, 'O'.code, 'g'.code, '@'.code, 'B'.code, '8'.code, '中'.code, '口'.code, 0x1D400)) {
            withGlyph(font.generateGlyph(codepoint)) { glyph ->
                assertEquals(font.getAdvance(codepoint), glyph.advance)
                val field = assertNotNull(glyph.glyphData)
                val alpha = assertNotNull(glyph.alphaData)
                assertEquals(glyph.width * glyph.height * 4, field.remaining())
                assertEquals(glyph.width * glyph.height, alpha.remaining())
                var mismatches = 0
                var inkPixels = 0
                for (index in 0 until alpha.remaining()) {
                    val coverage = alpha[index].toInt() and 255
                    val distance = median(field, index)
                    assertEquals(255, field[index * 4 + 3].toInt() and 255)
                    if (coverage >= 224) inkPixels++
                    if ((coverage >= 224 && distance < 126) || (coverage <= 31 && distance > 129)) mismatches++
                }
                assertTrue(inkPixels > 0, "No ink for U+${codepoint.toString(16)}")
                assertTrue(mismatches < maxOf(3, inkPixels / 30), "MSDF/STB polarity or alignment mismatch for U+${codepoint.toString(16)}: $mismatches/$inkPixels")
                assertTrue((0 until alpha.remaining()).any { index ->
                    val r = field[index * 4].toInt() and 255
                    val g = field[index * 4 + 1].toInt() and 255
                    val b = field[index * 4 + 2].toInt() and 255
                    abs(r - g) > 8 || abs(r - b) > 8
                }, "Glyph must contain distinct edge distance channels")
            }
        }
    }

    @Test
    fun whitespaceHasAdvanceWithoutAtlasPixels() = withFont { font ->
        withGlyph(font.generateGlyph(' ')) { glyph ->
            assertEquals(font.getAdvance(' '), glyph.advance)
            assertTrue(glyph.advance > 0)
            assertEquals(null, glyph.glyphData)
            assertEquals(null, glyph.alphaData)
            assertEquals(0, glyph.width)
            assertEquals(0, glyph.height)
        }
    }

    @Test
    fun destroyedFontRejectsNativeAccessAndCanBeClosedAgain() = withFont { font ->
        font.destroy()
        assertFailsWith<IllegalStateException> { font.generateGlyph('A') }
        assertFailsWith<IllegalStateException> { font.getAdvance('A') }
    }

    @Test
    fun fallbackHasSharpCornersAndAnEmptyHole() {
        val field = MsdfGlyphGenerator.generateFallback(24, 32, 4, 16)
        try {
            val width = 56
            assertTrue(median(field, 16 * width + 16) > 127)
            assertTrue(median(field, 17 * width + 17) > 127)
            assertTrue(median(field, 15 * width + 15) < 128)
            assertTrue(median(field, 32 * width + 28) < 128)
            // 双线性放大后，距两条外边各 0.1px 的点仍在方框墨迹内，不能变成圆角缺口。
            val corner = bilinearMedian(field, width, 16.1, 16.1)
            assertTrue(corner > 127.5, "Rounded MSDF corner: $corner")
        } finally {
            MemoryUtil.memFree(field)
        }
    }

    private fun withFont(block: (TtfFontFile) -> Unit) {
        val font = TtfFontFile(Path.of("../src/main/resources/assets/epsilon/fonts/font.ttf"), 72, 16)
        try {
            block(font)
        } finally {
            font.destroy()
        }
    }

    private fun withGlyph(glyph: TtfGlyph, block: (TtfGlyph) -> Unit) {
        try {
            block(glyph)
        } finally {
            MemoryUtil.memFree(glyph.glyphData)
            MemoryUtil.memFree(glyph.alphaData)
        }
    }

    private fun median(field: ByteBuffer, index: Int): Int {
        val r = field[index * 4].toInt() and 255
        val g = field[index * 4 + 1].toInt() and 255
        val b = field[index * 4 + 2].toInt() and 255
        return maxOf(minOf(r, g), minOf(maxOf(r, g), b))
    }

    private fun bilinearMedian(field: ByteBuffer, width: Int, x: Double, y: Double): Double {
        val x0 = (x - 0.5).toInt()
        val y0 = (y - 0.5).toInt()
        val fx = x - 0.5 - x0
        val fy = y - 0.5 - y0
        val channels = DoubleArray(3) { channel ->
            fun value(dx: Int, dy: Int) = (field[((y0 + dy) * width + x0 + dx) * 4 + channel].toInt() and 255).toDouble()
            (value(0, 0) * (1 - fx) + value(1, 0) * fx) * (1 - fy) +
                (value(0, 1) * (1 - fx) + value(1, 1) * fx) * fy
        }
        channels.sort()
        return channels[1]
    }
}
