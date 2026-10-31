package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import kotlin.math.abs
import kotlin.math.roundToInt

class DoubleSettingRow(private val doubleSetting: DoubleSetting) : SettingRow<DoubleSetting>(doubleSetting) {

    private val hoverAnimation = Animation(Easing.EASE_OUT_QUART, 150L)
    private val pressAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    private val indicatorAnimation = Animation(Easing.EASE_OUT_QUART, 150L)
    private var textMetrics: TextRenderer? = null
    private var dragging = false
    private var focusedState = false
    private var inputBuffer: String? = null
    private var cursorIndex = 0
    private var pendingValue: Double? = null

    init {
        hoverAnimation.startValue = 0.0f
        pressAnimation.startValue = 0.0f
        indicatorAnimation.startValue = 0.0f
    }

    override fun buildUi(
        scope: UiTree.Scope, guiGraphics: GuiGraphicsExtractor, textRenderer: TextRenderer,
        bounds: UiRect, hoverProgress: Float, mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        this.textMetrics = textRenderer
        val labelScale = 0.68f
        val labelY = (bounds.height - textRenderer.getHeight(labelScale)) / 2.0f
        hoverAnimation.run(if (dragging) 1.0f else hoverProgress)
        pressAnimation.run(if (dragging) 1.0f else 0.0f)
        indicatorAnimation.run(if (dragging || hoverProgress > 0.01f) 1.0f else 0.0f)

        val animatedHover = hoverAnimation.value
        val animatedPress = pressAnimation.value
        val indicatorProgress = indicatorAnimation.value

        scope.roundRect(
            0.0f, 0.0f, bounds.width, bounds.height,
            MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.rowSurface(animatedHover)
        )
        scope.text(doubleSetting.displayName, MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY)

        val trackBounds = getTrackBounds(bounds)
        val fieldBounds = getFieldBounds(bounds)
        val progress = getProgress()
        val visualProgress = (progress).coerceIn(0.0f, 1.0f)
        val handleWidth = 2.0f - animatedPress * 2.0f
        val handleHeight = 14.0f
        val handleX = trackBounds.x + trackBounds.width * visualProgress - handleWidth / 2.0f
        val handleGap = 2.5f

        scope.slider(
            trackBounds.relativeTo(bounds), visualProgress, 3.0f,
            MD3Theme.SECONDARY_CONTAINER,
            handleGap, 2.0f, MD3Theme.PRIMARY,
            handleWidth, handleHeight, 1.0f, MD3Theme.PRIMARY
        )

        if (indicatorProgress > 0.01f) {
            val label = formatValue()
            val textScale = 0.62f
            val bubbleWidth = textRenderer.getWidth(label, textScale) + 16.0f
            val bubbleHeight = 18.0f
            val bubbleX = handleX + handleWidth / 2.0f - bubbleWidth / 2.0f
            val bubbleY = bounds.y - 22.0f
            val bubbleAlpha = (255 * indicatorProgress).toInt()
            scope.pushAbsolute(UiRect(bubbleX, bubbleY, bubbleWidth, bubbleHeight)) { bubble ->
                bubble.roundRect(
                    0.0f, 0.0f, bubbleWidth, bubbleHeight, 9.0f,
                    MD3Theme.withAlpha(MD3Theme.INVERSE_SURFACE, bubbleAlpha)
                )
                val textWidth = textRenderer.getWidth(label, textScale)
                val textHeight = textRenderer.getHeight(textScale)
                val textX = (bubbleWidth - textWidth) / 2.0f
                val textY = (bubbleHeight - textHeight) / 2.0f
                bubble.text(
                    label, textX, textY, textScale,
                    MD3Theme.withAlpha(MD3Theme.INVERSE_ON_SURFACE, bubbleAlpha)
                )
            }
        }

        val fieldHover = animatedHover * 0.85f
        val display = if (focusedState) getDisplayBuffer() else formatValue()
        val displayScale = getFieldTextScale(textRenderer, display, fieldBounds)
        val displayWidth = textRenderer.getWidth(display, displayScale)
        val displayX = fieldBounds.x + (fieldBounds.width - displayWidth) / 2.0f
        scope.input(
            fieldBounds.relativeTo(bounds), focusedState, fieldHover,
            displayX - fieldBounds.x, display, displayScale, MD3Theme.filledFieldContent(focusedState),
            if (focusedState) minOf(cursorIndex, display.length) else null,
            if (focusedState) MD3Theme.filledFieldCaret(focusedState) else null,
            null, 0.0f, null
        )
    }

    fun getTrackBounds(bounds: UiRect): UiRect =
        UiRect(bounds.right() - MD3Theme.ROW_TRAILING_INSET - 116.0f, bounds.y + 12.0f, 72.0f, 6.0f)

    fun getFieldBounds(bounds: UiRect): UiRect =
        UiRect(bounds.right() - MD3Theme.ROW_TRAILING_INSET - 40.0f, bounds.y + 4.0f, 40.0f, 18.0f)

    override fun mouseClicked(bounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val fieldBounds = getFieldBounds(bounds)
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && fieldBounds.contains(event.x, event.y)) {
            dragging = false
            focusedState = true
            inputBuffer = formatPlainValue()
            cursorIndex = getCursorIndex(event.x, fieldBounds)
            return true
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT ||
            !getInteractiveBounds(bounds).contains(event.x, event.y)
        ) {
            return false
        }
        focusedState = false
        dragging = true
        updateFromMouse(bounds, event.x)
        return true
    }

    override fun mouseReleased(bounds: UiRect, event: MouseButtonEvent): Boolean {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && dragging) {
            commitPendingValue()
            dragging = false
            return true
        }
        dragging = false
        return false
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (!focusedState) {
            return false
        }
        when (event.key()) {
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                commitInput()
                focusedState = false
                return true
            }

            InputConstants.KEY_ESCAPE -> {
                focusedState = false
                inputBuffer = null
                return true
            }

            InputConstants.KEY_BACKSPACE -> {
                val buffer = inputBuffer
                if (buffer != null && cursorIndex > 0) {
                    inputBuffer = buffer.substring(0, cursorIndex - 1) + buffer.substring(cursorIndex)
                    cursorIndex--
                }
                return true
            }

            InputConstants.KEY_DELETE -> {
                val buffer = inputBuffer
                if (buffer != null && cursorIndex < buffer.length) {
                    inputBuffer = buffer.substring(0, cursorIndex) + buffer.substring(cursorIndex + 1)
                }
                return true
            }

            InputConstants.KEY_LEFT -> {
                cursorIndex = maxOf(0, cursorIndex - 1)
                return true
            }

            InputConstants.KEY_RIGHT -> {
                cursorIndex = minOf(getDisplayBuffer().length, cursorIndex + 1)
                return true
            }

            else -> return false
        }
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (!focusedState) {
            return false
        }
        val value = event.codepointAsString()
        if (!value.matches(Regex("[0-9.-]"))) {
            return false
        }
        val current = getDisplayBuffer()
        inputBuffer = current.substring(0, cursorIndex) + value + current.substring(cursorIndex)
        cursorIndex++
        return true
    }

    override fun setFocused(focused: Boolean) {
        if (!focused && this.focusedState) {
            commitInput()
            inputBuffer = null
        }
        this.focusedState = focused
        if (focused && inputBuffer == null) {
            inputBuffer = formatPlainValue()
            cursorIndex = inputBuffer!!.length
        }
    }

    override fun isFocused(): Boolean = focusedState

    override fun hasActiveAnimation(): Boolean =
        !hoverAnimation.isFinished || !pressAnimation.isFinished || !indicatorAnimation.isFinished

    fun updateFromMouse(bounds: UiRect, mouseX: Double) {
        val trackBounds = getTrackBounds(bounds)
        var progress = (mouseX - trackBounds.x) / trackBounds.width
        progress = (progress).coerceIn(0.0, 1.0)
        val rawValue = doubleSetting.min + (doubleSetting.max - doubleSetting.min) * progress
        val step = if (doubleSetting.step <= 0.0) 0.01 else doubleSetting.step
        val snapped = doubleSetting.min + ((rawValue - doubleSetting.min) / step).roundToInt() * step
        previewValue(snapped)
    }

    fun isDragging(): Boolean = dragging

    private fun getInteractiveBounds(bounds: UiRect): UiRect {
        val track = getTrackBounds(bounds)
        return UiRect(track.x, track.y - 6.0f, track.width, track.height + 12.0f)
    }

    private fun getProgress(): Float {
        if (doubleSetting.max <= doubleSetting.min) {
            return 0.0f
        }
        return ((getVisibleValue() - doubleSetting.min) / (doubleSetting.max - doubleSetting.min)).toFloat()
    }

    private fun formatValue(): String {
        val value = getVisibleValue()
        return if (abs(value - (value).roundToInt()) < 0.0001) {
            (value).roundToInt().toString()
        } else {
            String.format("%.2f", value)
        }
    }

    private fun formatPlainValue(): String = formatValue()

    private fun getDisplayBuffer(): String = inputBuffer ?: formatPlainValue()

    private fun commitInput() {
        val buffer = inputBuffer
        if (buffer == null || buffer.isBlank() || buffer == "-" || buffer == ".") {
            commitPendingValue()
            inputBuffer = formatPlainValue()
            cursorIndex = inputBuffer!!.length
            return
        }
        try {
            applyValueNow(buffer.toDouble())
        } catch (ignored: NumberFormatException) {
            // 非法输入保留原值。
        }
        inputBuffer = formatPlainValue()
        cursorIndex = inputBuffer!!.length
    }

    private fun getVisibleValue(): Double = pendingValue ?: doubleSetting.value

    private fun previewValue(value: Double) {
        if (doubleSetting.isApplyWhenRelease) {
            pendingValue = value
        } else {
            doubleSetting.value = value
        }
    }

    private fun applyValueNow(value: Double) {
        pendingValue = null
        doubleSetting.setUnboundedValue(value)
    }

    private fun commitPendingValue() {
        val value = pendingValue ?: return
        applyValueNow(value)
    }

    private fun getCursorIndex(mouseX: Double, fieldBounds: UiRect): Int {
        val text = getDisplayBuffer()
        val metrics = textMetrics()
        val scale = getFieldTextScale(metrics, text, fieldBounds)
        val textWidth = metrics.getWidth(text, scale)
        val textStart = fieldBounds.x + (fieldBounds.width - textWidth) / 2.0f
        for (i in 0..text.length) {
            val width = metrics.getWidth(text.substring(0, i), scale)
            if (mouseX <= textStart + width) {
                return i
            }
        }
        return text.length
    }

    private fun getFieldTextScale(textRenderer: TextRenderer, text: String, fieldBounds: UiRect): Float {
        val textWidth = textRenderer.getWidth(text, FIELD_TEXT_SCALE)
        val maxTextWidth = maxOf(1.0f, fieldBounds.width - FIELD_TEXT_PADDING * 2.0f)
        if (textWidth <= maxTextWidth || textWidth <= 0.0f) {
            return FIELD_TEXT_SCALE
        }
        return FIELD_TEXT_SCALE * maxTextWidth / textWidth
    }

    private fun textMetrics(): TextRenderer = textMetrics ?: FALLBACK_TEXT_METRICS

    companion object {

        private const val FIELD_TEXT_SCALE = 0.60f
        private const val FIELD_TEXT_PADDING = 5.0f

    }

}
