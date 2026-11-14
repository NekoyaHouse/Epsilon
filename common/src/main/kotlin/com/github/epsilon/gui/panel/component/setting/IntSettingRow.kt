package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.panel.component.RowSurface
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.render.animation.Easing
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import com.github.epsilon.settings.impl.IntSetting
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Color
import kotlin.math.roundToInt
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation

class IntSettingRow(private val intSetting: IntSetting) : SettingRow<IntSetting>(intSetting) {

    // 绘制入口是与本类同文件的 [UiScope.IntSettingRow]，以下状态需要对该扩展函数可见。
    internal val hoverAnimation = Animation(Easing.EASE_OUT_QUART, 150L)
    internal val pressAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    internal val indicatorAnimation = Animation(Easing.EASE_OUT_QUART, 150L)
    internal var textMetrics: UiTextMetrics? = null
    private var dragging by mutableStateOf(false)
    private var focusedState by mutableStateOf(false)
    private var inputBuffer: String? by mutableStateOf(null)
    internal var cursorIndex by mutableStateOf(0)
    private var pendingValue: Int? by mutableStateOf(null)

    init {
        hoverAnimation.startValue = 0.0f
        pressAnimation.startValue = 0.0f
        indicatorAnimation.startValue = 0.0f
    }

    fun getTrackBounds(bounds: UiRect): UiRect =
        localTrackBounds(bounds.width).translate(bounds.x, bounds.y)

    fun getFieldBounds(bounds: UiRect): UiRect =
        localFieldBounds(bounds.width).translate(bounds.x, bounds.y)

    /**
     * 滑条轨道的相对矩形；作用域原点即行左上角。
     */
    internal fun localTrackBounds(rowWidth: Float): UiRect =
        UiRect(rowWidth - MD3Theme.ROW_TRAILING_INSET - 116.0f, 12.0f, 72.0f, 6.0f)

    /**
     * 数值输入框的相对矩形；作用域原点即行左上角。
     */
    internal fun localFieldBounds(rowWidth: Float): UiRect =
        UiRect(rowWidth - MD3Theme.ROW_TRAILING_INSET - 40.0f, 4.0f, 40.0f, 18.0f)

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
        if (!value.matches(Regex("[0-9-]"))) {
            return false
        }
        val current = getDisplayBuffer()
        inputBuffer = current.substring(0, cursorIndex) + value + current.substring(cursorIndex)
        cursorIndex++
        return true
    }

    override fun cancelInput() {
        dragging = false; pendingValue = null
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

    fun updateFromMouse(bounds: UiRect, mouseX: Double) {
        val trackBounds = getTrackBounds(bounds)
        var progress = (mouseX - trackBounds.x) / trackBounds.width
        progress = (progress).coerceIn(0.0, 1.0)
        val rawValue = intSetting.range.first + (intSetting.range.last - intSetting.range.first) * progress
        val step = maxOf(1, intSetting.step)
        val snapped = intSetting.range.first + ((rawValue - intSetting.range.first) / step).roundToInt() * step
        previewValue(snapped)
    }

    fun isDragging(): Boolean = dragging

    private fun getInteractiveBounds(bounds: UiRect): UiRect {
        val track = getTrackBounds(bounds)
        return UiRect(track.x, track.y - 6.0f, track.width, track.height + 12.0f)
    }

    internal fun getProgress(): Float {
        if (intSetting.range.last <= intSetting.range.first) {
            return 0.0f
        }
        return ((getVisibleValue() - intSetting.range.first).toFloat() / (intSetting.range.last - intSetting.range.first).toFloat())
    }

    internal fun formatValue(): String = getVisibleValue().toString()

    private fun formatPlainValue(): String = getVisibleValue().toString()

    internal fun getDisplayBuffer(): String = inputBuffer ?: formatPlainValue()

    private fun commitInput() {
        val buffer = inputBuffer
        if (buffer == null || buffer.isBlank() || buffer == "-") {
            commitPendingValue()
            inputBuffer = formatPlainValue()
            cursorIndex = inputBuffer!!.length
            return
        }
        try {
            applyValueNow(buffer.toInt())
        } catch (ignored: NumberFormatException) {
            // 非法输入保留原值。
        }
        inputBuffer = formatPlainValue()
        cursorIndex = inputBuffer!!.length
    }

    private fun getVisibleValue(): Int = pendingValue ?: intSetting.value

    private fun previewValue(value: Int) {
        if (intSetting.isApplyWhenRelease) {
            pendingValue = value
        } else {
            intSetting.setUnboundedValue(value)
        }
    }

    private fun applyValueNow(value: Int) {
        pendingValue = null
        intSetting.setUnboundedValue(value)
    }

    private fun commitPendingValue() {
        val value = pendingValue ?: return
        applyValueNow(value)
    }

    private fun getCursorIndex(mouseX: Double, fieldBounds: UiRect): Int {
        val text = getDisplayBuffer()
        val metrics = textMetrics()
        val scale = getFieldTextScale(metrics, text, fieldBounds)
        val textWidth = metrics.textWidth(text, scale)
        val textStart = fieldBounds.x + (fieldBounds.width - textWidth) / 2.0f
        for (i in 0..text.length) {
            val width = metrics.textWidth(text.substring(0, i), scale)
            if (mouseX <= textStart + width) {
                return i
            }
        }
        return text.length
    }

    internal fun getFieldTextScale(metrics: UiTextMetrics, text: String, fieldBounds: UiRect): Float {
        val textWidth = metrics.textWidth(text, FIELD_TEXT_SCALE)
        val maxTextWidth = maxOf(1.0f, fieldBounds.width - FIELD_TEXT_PADDING * 2.0f)
        if (textWidth <= maxTextWidth || textWidth <= 0.0f) {
            return FIELD_TEXT_SCALE
        }
        return FIELD_TEXT_SCALE * maxTextWidth / textWidth
    }

    private fun textMetrics(): UiTextMetrics = textMetrics ?: FALLBACK_TEXT_METRICS

    companion object {

        private const val FIELD_TEXT_SCALE = 0.60f
        private const val FIELD_TEXT_PADDING = 5.0f

    }

}

/**
 * 绘制一行整数设置；作用域原点即行左上角，所有坐标都是行局部坐标。
 */
fun UiScope.IntSettingRow(row: IntSettingRow, hoverProgress: Float) {
    val setting = row.getSetting()
    row.textMetrics = metrics
    val labelScale = 0.68f
    val labelY = (height - metrics.textHeight(labelScale)) / 2.0f
    row.hoverAnimation.run(if (row.isDragging()) 1.0f else hoverProgress)
    row.pressAnimation.run(if (row.isDragging()) 1.0f else 0.0f)
    row.indicatorAnimation.run(if (row.isDragging() || hoverProgress > 0.01f) 1.0f else 0.0f)

    val animatedHover = row.hoverAnimation.value
    val animatedPress = row.pressAnimation.value
    val indicatorProgress = row.indicatorAnimation.value

    RowSurface(animatedHover)
    Text(setting.displayName, MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY)

    val trackBounds = row.localTrackBounds(width)
    val fieldBounds = row.localFieldBounds(width)
    val progress = row.getProgress()
    val visualProgress = progress.coerceIn(0.0f, 1.0f)
    val handleWidth = 2.0f - animatedPress * 2.0f
    val handleHeight = 14.0f
    val handleX = trackBounds.x + trackBounds.width * visualProgress - handleWidth / 2.0f
    val handleGap = 2.5f

    Slider(
        trackBounds, visualProgress, 3.0f,
        MD3Theme.SECONDARY_CONTAINER,
        handleGap, 2.0f, MD3Theme.PRIMARY,
        handleWidth, handleHeight, 1.0f, MD3Theme.PRIMARY
    )

    if (indicatorProgress > 0.01f) {
        val label = row.formatValue()
        val textScale = 0.62f
        val bubbleWidth = metrics.textWidth(label, textScale) + 16.0f
        val bubbleHeight = 18.0f
        val bubbleX = handleX + handleWidth / 2.0f - bubbleWidth / 2.0f
        // 数值气泡在旧实现里是行绝对坐标 bounds.y - 22，换成行局部坐标就是 -22
        val bubbleY = -22.0f
        val bubbleAlpha = (255 * indicatorProgress).toInt()
        Positioned(bubbleX, bubbleY, bubbleWidth, bubbleHeight) {
            RoundRect(
                0.0f, 0.0f, bubbleWidth, bubbleHeight, 9.0f,
                MD3Theme.withAlpha(MD3Theme.INVERSE_SURFACE, bubbleAlpha)
            )
            val textWidth = metrics.textWidth(label, textScale)
            val textHeight = metrics.textHeight(textScale)
            val textX = (bubbleWidth - textWidth) / 2.0f
            val textY = (bubbleHeight - textHeight) / 2.0f
            Text(
                label, textX, textY, textScale,
                MD3Theme.withAlpha(MD3Theme.INVERSE_ON_SURFACE, bubbleAlpha)
            )
        }
    }

    val fieldHover = animatedHover * 0.85f
    val focused = row.isFocused()
    val display = if (focused) row.getDisplayBuffer() else row.formatValue()
    val displayScale = row.getFieldTextScale(metrics, display, fieldBounds)
    val displayWidth = metrics.textWidth(display, displayScale)
    val displayX = fieldBounds.x + (fieldBounds.width - displayWidth) / 2.0f
    Input(
        fieldBounds, focused, fieldHover,
        0.0f, Color(0, 0, 0, 0), 0.0f,
        displayX - fieldBounds.x, display, displayScale, MD3Theme.filledFieldContent(focused),
        null, null,
        if (focused) minOf(row.cursorIndex, display.length) else null,
        if (focused) MD3Theme.filledFieldCaret(focused) else null,
        null, 0.0f, null
    )
}
