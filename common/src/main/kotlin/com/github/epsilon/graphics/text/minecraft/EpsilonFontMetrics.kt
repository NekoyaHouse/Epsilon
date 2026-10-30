package com.github.epsilon.graphics.text.minecraft

import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.StringDecomposer

object EpsilonFontMetrics {

    private const val REPLACEMENT_LINE_HEIGHT = 10.0f
    internal const val MINECRAFT_BASELINE = 7.0f
    private val FONT_ID: Identifier = ResourceLocationUtils.getIdentifier("fonts/font.ttf")

    const val LETTER_SPACING = 0f
    const val SPACE_WIDTH = 3.0f

    private var cachedFont: TtfFontLoader? = null

    @JvmStatic
    fun font(): TtfFontLoader? {
        val current = cachedFont
        if (current != null) {
            return current
        }

        return try {
            TtfFontLoader(FONT_ID).also { cachedFont = it }
        } catch (ignored: RuntimeException) {
            null
        }
    }

    @JvmStatic
    fun minecraftScale(font: TtfFontLoader): Float {
        return REPLACEMENT_LINE_HEIGHT / font.fontFile.fontHeight
    }

    @JvmStatic
    fun advance(codepoint: Int, style: Style, font: TtfFontLoader?): Float {
        if (Character.isWhitespace(codepoint)) {
            return SPACE_WIDTH + (if (style.isBold) 1.0f else 0.0f)
        }
        if (font == null) {
            return 0.0f
        }
        if (codepoint < Character.MIN_CODE_POINT || codepoint > Character.MAX_CODE_POINT) {
            return 0.0f
        }

        return font.getAdvance(codepoint) * minecraftScale(font) + LETTER_SPACING + (if (style.isBold) 1.0f else 0.0f)
    }

    @JvmStatic
    fun width(text: String): Float? {
        val loader = font() ?: return null

        var total = 0.0f
        StringDecomposer.iterateFormatted(text, Style.EMPTY) { _, style, codepoint ->
            total += advance(codepoint, style, loader)
            true
        }
        return total
    }

    @JvmStatic
    fun width(text: FormattedCharSequence): Float? {
        val loader = font() ?: return null

        var total = 0.0f
        text.accept { _, style, codepoint ->
            total += advance(codepoint, style, loader)
            true
        }
        return total
    }

    @JvmStatic
    fun width(text: FormattedText): Float? {
        val loader = font() ?: return null

        var total = 0.0f
        StringDecomposer.iterateFormatted(text, Style.EMPTY) { _, style, codepoint ->
            total += advance(codepoint, style, loader)
            true
        }
        return total
    }
}
