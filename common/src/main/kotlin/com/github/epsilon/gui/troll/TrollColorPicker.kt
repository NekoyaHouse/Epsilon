package com.github.epsilon.gui.troll

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.settings.impl.ColorSetting
import com.mojang.blaze3d.platform.InputConstants
import kotlin.math.roundToInt
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Color

class TrollColorPicker(private val setting: ColorSetting, x: Float, y: Float) : TrollWindow() {

    private val original: Color = setting.value
    private val sliders: Array<UiRect> = Array(4) { UiRect(0.0f, 0.0f, 0.0f, 0.0f) }
    private val buttons: Array<UiRect> = Array(3) { UiRect(0.0f, 0.0f, 0.0f, 0.0f) }
    private lateinit var field: UiRect
    private lateinit var hueBar: UiRect
    private lateinit var previousColor: UiRect
    private lateinit var currentColor: UiRect
    private var hue: Float = 0.0f
    private var saturation: Float = 0.0f
    private var brightness: Float = 0.0f
    private var alpha: Int = 255
    private var draggingPart: Int = -1
    private var closed: Boolean = false

    init {
        setRgb(original)
        alpha = if (setting.isAllowAlpha()) original.alpha else 255
        position(x, y)
    }

    override fun layout(metrics: TextRenderer, screenWidth: Float, screenHeight: Float) {
        headerHeight = TrollTheme.headerHeight(metrics)
        var step = TrollTheme.rowHeight(metrics) + 3.0f
        val channels = channelCount()
        val contentTop = headerHeight + CONTENT_PADDING
        size(
            minOf(441.0f, screenWidth),
            minOf(contentTop + (channels + 3) * step - 3.0f + CONTENT_PADDING, screenHeight),
            screenWidth, screenHeight
        )
        step = ((bounds.height - contentTop - CONTENT_PADDING + 3.0f) / (channels + 3)).coerceIn(1.0f, step)
        val fieldSize = (bounds.width * 0.48f - 27.0f).coerceIn(18.0f, bounds.height - contentTop - CONTENT_PADDING)
        field = UiRect(bounds.x + 6.0f, bounds.y + contentTop, fieldSize, fieldSize)
        hueBar = UiRect(field.right() + 9.0f, field.y, 12.0f, fieldSize)
        val sliderX = hueBar.right() + 12.0f
        val sliderWidth = maxOf(1.0f, bounds.right() - 6.0f - sliderX)
        for (i in 0 until channels) {
            sliders[i] = UiRect(sliderX, field.y + i * step, sliderWidth, step - 3.0f)
        }
        val buttonWidth = minOf(87.0f, sliderWidth * 0.48f)
        for (i in buttons.indices) {
            buttons[i] = UiRect(
                bounds.right() - 6.0f - buttonWidth,
                field.y + (channels + i) * step, buttonWidth, step - 3.0f
            )
        }
        val previewWidth = maxOf(1.0f, (sliderWidth - buttonWidth - 9.0f) / 2.0f)
        previousColor = UiRect(sliderX, buttons[0].y, previewWidth, step * 3.0f - 3.0f)
        currentColor = previousColor.translate(previewWidth + 3.0f, 0.0f)
    }

    override fun draw(scope: UiTree.Scope, metrics: TextRenderer, mouseX: Int, mouseY: Int) {
        TrollTheme.window(scope, bounds, headerHeight, EpsilonTranslations.TrollGui.COLOR_PICKER.getTranslatedName(), metrics)
        val hueColor = Color.getHSBColor(hue, 1.0f, 1.0f)
        scope.rectGradient(1, field.x, field.y, field.width, field.height, Color.WHITE, Color.WHITE, hueColor, hueColor)
        scope.rectGradient(
            1, field.x, field.y, field.width, field.height,
            Color(0, 0, 0, 0), Color.BLACK, Color.BLACK, Color(0, 0, 0, 0)
        )
        for (i in 0 until 6) {
            val top = Color.getHSBColor(i / 6.0f, 1.0f, 1.0f)
            val bottom = Color.getHSBColor((i + 1) / 6.0f, 1.0f, 1.0f)
            scope.rectGradient(
                1, hueBar.x, hueBar.y + hueBar.height * i / 6.0f, hueBar.width, hueBar.height / 6.0f + 0.3f,
                top, bottom, bottom, top
            )
        }
        scope.rectOutline(
            2, field.x + saturation * field.width - 3.0f, field.y + (1.0f - brightness) * field.height - 3.0f,
            6.0f, 6.0f, 0.8f, Color.WHITE
        )
        scope.rect(2, hueBar.x - 3.0f, hueBar.y + hue * hueBar.height - 1.5f, hueBar.width + 6.0f, 3.0f, Color.WHITE)
        val current = color()
        val values = intArrayOf(current.red, current.green, current.blue, alpha)
        val fills = arrayOf(
            Color(values[0], 0, 0), Color(0, values[1], 0), Color(0, 0, values[2]), Color.WHITE
        )
        for (i in 0 until channelCount()) {
            val slider = sliders[i]
            scope.rect(1, slider.x, slider.y, slider.width, slider.height, Color(0, 0, 0, 40))
            scope.rect(1, slider.x, slider.y, slider.width * values[i] / 255.0f, slider.height, TrollTheme.alpha(fills[i], 200))
            scope.text(
                3, TrollTheme.trim(CHANNELS[i].getTranslatedName(), metrics, TrollTheme.TEXT_SCALE, slider.width - 33.0f),
                slider.x + 3.0f, slider.y + 2.25f, TrollTheme.TEXT_SCALE, TrollTheme.text()
            )
            val value = values[i].toString()
            scope.text(
                3, value, slider.right() - metrics.getWidth(value, TrollTheme.VALUE_SCALE) - 3.0f,
                slider.bottom() - metrics.getHeight(TrollTheme.VALUE_SCALE) - 3.0f, TrollTheme.VALUE_SCALE, TrollTheme.text()
            )
        }
        drawPreview(scope, previousColor, original)
        drawPreview(scope, currentColor, current)
        val labels = arrayOf(
            EpsilonTranslations.TrollGui.OKAY,
            EpsilonTranslations.TrollGui.CANCEL,
            EpsilonTranslations.TrollGui.APPLY
        )
        for (i in buttons.indices) {
            val button = buttons[i]
            scope.rect(
                1, button.x, button.y, button.width, button.height,
                TrollTheme.alpha(TrollTheme.primary(), if (button.contains(mouseX.toDouble(), mouseY.toDouble())) 180 else 70)
            )
            val label = TrollTheme.trim(labels[i].getTranslatedName(), metrics, TrollTheme.TEXT_SCALE, button.width - 6.0f)
            scope.text(
                3, label, button.centerX() - metrics.getWidth(label, TrollTheme.TEXT_SCALE) / 2.0f,
                button.y + 2.25f, TrollTheme.TEXT_SCALE, TrollTheme.text()
            )
        }
    }

    private fun drawPreview(scope: UiTree.Scope, bounds: UiRect, color: Color) {
        scope.rect(1, bounds.x, bounds.y, bounds.width, bounds.height, Color.WHITE)
        scope.rect(1, bounds.x, bounds.centerY(), bounds.width, bounds.height / 2.0f, Color.BLACK)
        scope.rect(2, bounds.x, bounds.y, bounds.width, bounds.height, color)
    }

    override fun mouseClicked(event: MouseButtonEvent): Boolean {
        if (super.mouseClicked(event)) return true
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return true
        if (field.contains(event.x(), event.y())) {
            draggingPart = 0
        } else if (hueBar.contains(event.x(), event.y())) {
            draggingPart = 1
        } else {
            for (i in 0 until channelCount()) {
                if (sliders[i].contains(event.x(), event.y())) draggingPart = i + 2
            }
            for (i in buttons.indices) {
                if (buttons[i].contains(event.x(), event.y())) {
                    if (i == 1) cancel()
                    else {
                        setting.value = color()
                        if (i == 0) closed = true
                    }
                    return true
                }
            }
        }
        update(event.x(), event.y())
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent): Boolean {
        if (super.mouseDragged(event)) return true
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) update(event.x(), event.y())
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent) {
        super.mouseReleased(event)
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) draggingPart = -1
    }

    fun confirm() {
        setting.value = color()
        closed = true
    }

    fun cancel() {
        setting.value = original
        closed = true
    }

    fun isClosed(): Boolean {
        return closed
    }

    private fun update(x: Double, y: Double) {
        if (draggingPart == 0) {
            saturation = ((x - field.x) / field.width).coerceIn(0.0, 1.0).toFloat()
            brightness = (1.0 - (y - field.y) / field.height).coerceIn(0.0, 1.0).toFloat()
        } else if (draggingPart == 1) {
            hue = ((y - hueBar.y) / hueBar.height).coerceIn(0.0, 1.0).toFloat()
        } else if (draggingPart >= 2) {
            val channel = draggingPart - 2
            val slider = sliders[channel]
            val value = (((x - slider.x) / slider.width).coerceIn(0.0, 1.0) * 255.0).roundToInt()
            val current = color()
            when (channel) {
                0 -> setRgb(Color(value, current.green, current.blue))
                1 -> setRgb(Color(current.red, value, current.blue))
                2 -> setRgb(Color(current.red, current.green, value))
                3 -> alpha = value
            }
        }
    }

    private fun setRgb(color: Color) {
        val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
        hue = hsb[0]
        saturation = hsb[1]
        brightness = hsb[2]
    }

    private fun color(): Color {
        return TrollTheme.alpha(Color.getHSBColor(hue, saturation, brightness), alpha)
    }

    private fun channelCount(): Int {
        return if (setting.isAllowAlpha()) 4 else 3
    }

    companion object {
        private const val CONTENT_PADDING = 9.0f

        private val CHANNELS: Array<TranslateComponent> = arrayOf(
            EpsilonTranslations.TrollGui.RED, EpsilonTranslations.TrollGui.GREEN,
            EpsilonTranslations.TrollGui.BLUE, EpsilonTranslations.TrollGui.ALPHA
        )
    }

}
