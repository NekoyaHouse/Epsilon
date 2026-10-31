package com.github.epsilon.elements.impl

import com.github.epsilon.Constants
import com.github.epsilon.elements.HudModule
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.graphics.text.TextGlitchEffect
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.utils.render.ColorUtils
import com.google.common.base.Suppliers
import net.minecraft.client.DeltaTracker
import java.awt.Color
import java.util.function.Supplier

object Watermark : HudModule("Watermark", 0f, 0f, 200f, 28f) {

    private enum class Mode {
        Tradition,
        Glitch,
        Sakura,
    }

    private enum class FontMode {
        Default,
        OsakaChips,
    }

    private enum class ColorMode {
        Single,
        Double,
    }

    private val text by stringSetting("Text", Constants.NAME)
    private val mode by enumSetting("Style", Mode.Tradition)
    private val fontMode by enumSetting("Font Mode", FontMode.OsakaChips)
    private val scale by doubleSetting("Scale", 1.0, 0.5..2.0, 0.1)
    private val textColor by colorSetting(
        "Text Color",
        Color(255, 255, 255, 235), true, { mode == Mode.Tradition })
    private val colorMode by enumSetting("Color Mode", ColorMode.Single, { mode == Mode.Sakura })
    private val singleColor by colorSetting(
        "Color",
        Color.WHITE,
        false, { mode == Mode.Sakura && colorMode == ColorMode.Single })
    private val doubleColor1 by colorSetting(
        "Color 1",
        Color.WHITE,
        false, { mode == Mode.Sakura && colorMode == ColorMode.Double })
    private val doubleColor2 by colorSetting(
        "Color 2",
        Color.RED,
        false, { mode == Mode.Sakura && colorMode == ColorMode.Double })
    private val glow by boolSetting("Glow", true, { mode == Mode.Sakura })
    private val glowRadius by doubleSetting(
        "Glow Radius",
        3.0,
        0.1..6.0,
        0.1, { mode == Mode.Sakura && glow })
    private val glowIntensity by intSetting("Glow Intensity", 2, 1..5, 1, { mode == Mode.Sakura && glow })
    private val glitchColor by colorSetting("Glitch Color", Color.WHITE, false, { mode == Mode.Glitch })
    private val chromaticX by doubleSetting("Chromatic X", 1.5, 0.0..6.0, 0.1, { mode == Mode.Glitch })
    private val chromaticY by doubleSetting("Chromatic Y", 0.0, 0.0..6.0, 0.1, { mode == Mode.Glitch })
    private val glitchGlowRadius by doubleSetting(
        "Glitch Glow Radius",
        2.5,
        0.0..10.0,
        0.1, { mode == Mode.Glitch })
    private val glitchGlowIntensity by doubleSetting(
        "Glitch Glow Intensity",
        1.6,
        0.0..3.0,
        0.1, { mode == Mode.Glitch })
    private val sliceHeight by doubleSetting("Slice Height", 4.0, 1.0..32.0, 0.5, { mode == Mode.Glitch })
    private val sliceAmount by doubleSetting("Slice Amount", 2.5, 0.0..10.0, 0.1, { mode == Mode.Glitch })
    private val glitchStrength by doubleSetting(
        "Glitch Strength",
        0.65,
        0.0..1.0,
        0.05, { mode == Mode.Glitch })
    private val scanlineStrength by doubleSetting(
        "Scanline Strength",
        0.45,
        0.0..1.0,
        0.05, { mode == Mode.Glitch })
    private val noiseStrength by doubleSetting(
        "Noise Strength",
        0.25,
        0.0..1.0,
        0.05, { mode == Mode.Glitch })

    private val textRendererSupplier: Supplier<TextRenderer> =
        Suppliers.memoize(com.google.common.base.Supplier { TextRenderer.create() })

    override fun render(deltaTracker: DeltaTracker) {
        val textRenderer = textRendererSupplier.get()
        val scaledScale = scale.toFloat() * 2f
        when (mode) {
            Mode.Tradition -> renderTradition(textRenderer, scaledScale)
            Mode.Glitch -> renderGlitch(textRenderer, scaledScale)
            Mode.Sakura -> renderSakura(textRenderer, scaledScale)
        }
    }

    private fun renderTradition(textRenderer: TextRenderer, scale: Float) {
        val text = this.text
        val font = font()
        renderScope().text(text, x, y, scale, textColor, font)
        val totalWidth = textRenderer.getWidth(text, scale, font)
        val totalHeight = textRenderer.getHeight(scale, font)
        setBounds(totalWidth, totalHeight)
    }

    private fun renderGlitch(textRenderer: TextRenderer, scale: Float) {
        val text = this.text
        val font = font()
        val effect = TextGlitchEffect(
            chromaticX.toFloat(),
            chromaticY.toFloat(),
            glitchGlowRadius.toFloat(),
            glitchGlowIntensity.toFloat(),
            sliceHeight.toFloat(),
            sliceAmount.toFloat(),
            glitchStrength.toFloat(),
            scanlineStrength.toFloat(),
            glitchColor,
            noiseStrength.toFloat()
        )

        renderScope().glitchText(text, x, y, scale, Color.WHITE, effect, font)
        setBounds(textRenderer.getWidth(text, scale, font), textRenderer.getHeight(scale, font))
    }

    private fun renderSakura(textRenderer: TextRenderer, scale: Float) {
        val text = this.text
        val font = font()
        val totalWidth = textRenderer.getWidth(text, scale, font)
        val totalHeight = textRenderer.getHeight(scale, font)
        var textX = this.x
        val scope: UiTree.Scope = renderScope()

        for (index in text.indices) {
            val character = text[index].toString()
            val characterWidth = textRenderer.getWidth(character, scale, font)

            val color = if (colorMode == ColorMode.Single) {
                singleColor
            } else {
                animateColor(
                    doubleColor1,
                    doubleColor2,
                    if (width <= 0.0f) index * 100L else ((textX - this.x) / totalWidth * 1600.0f).toLong()
                )
            }

            val shadowColor = Color(
                (color.red * 0.7f).toInt(),
                (color.green * 0.7f).toInt(),
                (color.blue * 0.7f).toInt(),
                160
            )

            scope.text(character, textX + 1.0f, this.y + 1.0f, scale, shadowColor, font) // shadow
            if (glow) {
                scope.blurredText(character, textX, this.y, scale, glowRadius.toFloat() * scale, glowIntensity, color)
            }
            scope.text(character, textX, this.y, scale, color, font)

            textX += characterWidth
        }

        setBounds(totalWidth, totalHeight)
    }

    private fun animateColor(start: Color, end: Color, offset: Long): Color {
        var progress = ((System.currentTimeMillis() + offset) % 4000L) / 2000.0
        if (progress > 1.0) {
            progress = 1.0 - progress % 1.0
        }
        return ColorUtils.interpolateColor(start, end, progress.toFloat())
    }

    private fun font(): TtfFontLoader {
        return when (fontMode) {
            FontMode.Default -> StaticFontLoader.defaultFont()
            FontMode.OsakaChips -> StaticFontLoader.OSAKA_CHIPS
        }
    }

}
