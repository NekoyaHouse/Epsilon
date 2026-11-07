package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.kernel.state.*
import com.github.epsilon.gui.lib.Input
import com.github.epsilon.gui.lib.Text
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.panel.component.RowSurface
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.github.epsilon.settings.impl.StringSetting
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Color

class StringSettingRow(private val stringSetting: StringSetting) : SettingRow<StringSetting>(stringSetting) {

    // 绘制入口是与本类同文件的 [UiScope.StringSettingRow]，度量需要对该扩展函数可见。
    internal var textMetrics: UiTextMetrics? = null
    private var focusedState by mutableStateOf(false)
    private var inputBuffer: String? by mutableStateOf<String?>(null)
    private var cursorIndex by mutableStateOf(0)
    private var selectionAnchor by mutableStateOf(-1)

    override fun mouseClicked(bounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }
        val fieldBounds = getFieldBounds(bounds)
        if (!fieldBounds.contains(event.x, event.y)) {
            return false
        }
        val wasFocused = focusedState
        focusedState = true
        if (!wasFocused) IMEFocusHelper.activate()
        if (!wasFocused || inputBuffer == null) {
            inputBuffer = normalize(stringSetting.value)
        }
        cursorIndex = getCursorIndex(event.x, fieldBounds)
        clearSelection()
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (!focusedState) {
            return false
        }
        if (isControlDown()) {
            return handleControlShortcut(event.key())
        }
        when (event.key()) {
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                commitInput()
                focusedState = false
                IMEFocusHelper.deactivate()
                clearSelection()
                return true
            }

            InputConstants.KEY_ESCAPE -> {
                focusedState = false
                IMEFocusHelper.deactivate()
                inputBuffer = null
                clearSelection()
                return true
            }

            InputConstants.KEY_BACKSPACE -> {
                deleteBackward()
                return true
            }

            InputConstants.KEY_DELETE -> {
                deleteForward()
                return true
            }

            InputConstants.KEY_LEFT -> {
                cursorIndex = maxOf(0, cursorIndex - 1)
                clearSelection()
                return true
            }

            InputConstants.KEY_RIGHT -> {
                cursorIndex = minOf(getDisplayBuffer().length, cursorIndex + 1)
                clearSelection()
                return true
            }

            InputConstants.KEY_HOME -> {
                cursorIndex = 0
                clearSelection()
                return true
            }

            InputConstants.KEY_END -> {
                cursorIndex = getDisplayBuffer().length
                clearSelection()
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
        if (value.isEmpty()) {
            return false
        }
        val current = getDisplayBuffer()
        val selectionLength = if (hasSelection()) selectionEnd - selectionStart else 0
        if (current.length - selectionLength >= MAX_LENGTH) {
            return false
        }
        replaceSelection(value)
        return true
    }

    override fun setFocused(focused: Boolean) {
        if (!focused && this.focusedState) {
            commitInput()
            inputBuffer = null
            IMEFocusHelper.deactivate()
        }
        this.focusedState = focused
        if (focused && inputBuffer == null) {
            inputBuffer = normalize(stringSetting.value)
            cursorIndex = inputBuffer!!.length
            IMEFocusHelper.activate()
        }
        if (!focused) {
            clearSelection()
        }
    }

    override fun isFocused(): Boolean = focusedState

    private fun getFieldBounds(bounds: UiRect): UiRect =
        localFieldBounds(bounds.width).translate(bounds.x, bounds.y)

    /**
     * 输入框的行局部矩形；作用域原点即行左上角。
     */
    internal fun localFieldBounds(rowWidth: Float): UiRect = UiRect(
        rowWidth - MD3Theme.ROW_TRAILING_INSET - FIELD_WIDTH,
        4.0f,
        FIELD_WIDTH,
        18.0f
    )

    internal fun getDisplayBuffer(): String = inputBuffer ?: normalize(stringSetting.value)

    private fun commitInput() {
        var value = inputBuffer ?: normalize(stringSetting.value)
        value = if (value.length > MAX_LENGTH) value.substring(0, MAX_LENGTH) else value
        applyValue(value)
        inputBuffer = value
        cursorIndex = value.length
        clearSelection()
    }

    private fun getCursorIndex(mouseX: Double, fieldBounds: UiRect): Int {
        val text = getDisplayBuffer()
        val slice = buildDisplaySlice(text, fieldBounds, true)
        val metrics = textMetrics()
        for (i in 0..slice.text.length) {
            val width = metrics.textWidth(slice.text.substring(0, i), FIELD_SCALE)
            if (mouseX <= slice.textX + width) {
                return slice.start + i
            }
        }
        return slice.start + slice.text.length
    }

    internal fun buildDisplaySlice(value: String?, fieldBounds: UiRect, editing: Boolean): DisplaySlice {
        val safeValue = value ?: ""
        val horizontalInset = 6.0f
        val availableWidth = maxOf(8.0f, fieldBounds.width - horizontalInset * 2.0f)
        if (!editing) {
            val shown = fitWithEllipsis(safeValue, availableWidth)
            return DisplaySlice(shown, fieldBounds.x + horizontalInset, 0, 0, shown.length)
        }

        val safeCursor = cursorIndex.coerceIn(0, safeValue.length)
        val metrics = textMetrics()

        if (metrics.textWidth(safeValue, FIELD_SCALE) <= availableWidth) {
            return DisplaySlice(safeValue, fieldBounds.x + horizontalInset, safeCursor, 0, safeValue.length)
        }

        var low = 0
        var high = safeCursor
        var bestStart = safeCursor
        while (low <= high) {
            val mid = (low + high) / 2
            val beforeCaret = safeValue.substring(mid, safeCursor)
            if (metrics.textWidth(beforeCaret, FIELD_SCALE) <= availableWidth - 2.0f) {
                bestStart = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        val start = bestStart

        low = safeCursor
        high = safeValue.length
        var bestEnd = safeCursor
        while (low <= high) {
            val mid = (low + high) / 2
            val candidate = safeValue.substring(start, mid)
            if (metrics.textWidth(candidate, FIELD_SCALE) <= availableWidth) {
                bestEnd = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        val end = bestEnd
        val shown = safeValue.substring(start, end)
        return DisplaySlice(shown, fieldBounds.x + horizontalInset, safeCursor - start, start, end)
    }

    private fun fitWithEllipsis(value: String, availableWidth: Float): String {
        val metrics = textMetrics()
        if (metrics.textWidth(value, FIELD_SCALE) <= availableWidth) {
            return value
        }
        val ellipsis = "..."
        val ellipsisWidth = metrics.textWidth(ellipsis, FIELD_SCALE)
        if (ellipsisWidth >= availableWidth) {
            return ""
        }
        var low = 0
        var high = value.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            val candidate = value.substring(0, mid) + ellipsis
            if (metrics.textWidth(candidate, FIELD_SCALE) <= availableWidth) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        return value.substring(0, low) + ellipsis
    }

    internal fun normalize(value: String?): String = value ?: ""

    private fun handleControlShortcut(key: Int): Boolean = when (key) {
        InputConstants.KEY_A -> {
            selectAll()
            true
        }

        InputConstants.KEY_C -> {
            copySelection()
            true
        }

        InputConstants.KEY_V -> {
            pasteClipboard()
            true
        }

        else -> false
    }

    private fun selectAll() {
        val current = getDisplayBuffer()
        cursorIndex = current.length
        selectionAnchor = 0
    }

    private fun copySelection() {
        if (!hasSelection()) {
            return
        }
        val current = getDisplayBuffer()
        mc.keyboardHandler.clipboard = current.substring(selectionStart, selectionEnd)
    }

    private fun pasteClipboard() {
        val clipboard = mc.keyboardHandler.clipboard
        if (clipboard.isEmpty()) {
            return
        }
        var sanitized = sanitizeClipboard(clipboard)
        if (sanitized.isEmpty()) {
            return
        }
        val current = getDisplayBuffer()
        val selectionLength = if (hasSelection()) selectionEnd - selectionStart else 0
        val available = MAX_LENGTH - (current.length - selectionLength)
        if (available <= 0) {
            return
        }
        if (sanitized.length > available) {
            sanitized = sanitized.substring(0, available)
        }
        replaceSelection(sanitized)
    }

    private fun sanitizeClipboard(value: String): String {
        val builder = StringBuilder(value.length)
        value.codePoints().forEach { codePoint ->
            if (codePoint >= 32 && codePoint != 127) {
                builder.appendCodePoint(codePoint)
            }
        }
        return builder.toString()
    }

    private fun replaceSelection(value: String) {
        val current = getDisplayBuffer()
        val start = if (hasSelection()) selectionStart else cursorIndex
        val end = if (hasSelection()) selectionEnd else cursorIndex
        inputBuffer = current.substring(0, start) + value + current.substring(end)
        cursorIndex = start + value.length
        clearSelection()
    }

    private fun deleteBackward() {
        if (hasSelection()) {
            replaceSelection("")
            return
        }
        val buffer = inputBuffer
        if (buffer != null && cursorIndex > 0) {
            inputBuffer = buffer.substring(0, cursorIndex - 1) + buffer.substring(cursorIndex)
            cursorIndex--
        }
    }

    private fun deleteForward() {
        if (hasSelection()) {
            replaceSelection("")
            return
        }
        val buffer = inputBuffer
        if (buffer != null && cursorIndex < buffer.length) {
            inputBuffer = buffer.substring(0, cursorIndex) + buffer.substring(cursorIndex + 1)
        }
    }

    private fun applyValue(value: String) {
        if (stringSetting.value != value) {
            stringSetting.value = value
        }
    }

    internal fun hasSelection(): Boolean = selectionAnchor >= 0 && selectionAnchor != cursorIndex

    internal val selectionStart: Int
        get() = minOf(selectionAnchor, cursorIndex)

    internal val selectionEnd: Int
        get() = maxOf(selectionAnchor, cursorIndex)

    private fun clearSelection() {
        selectionAnchor = -1
    }

    private fun isControlDown(): Boolean =
        InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL)

    internal fun textMetrics(): UiTextMetrics = textMetrics ?: FALLBACK_TEXT_METRICS

    internal data class DisplaySlice(
        val text: String,
        val textX: Float,
        val caretIndex: Int,
        val start: Int,
        val end: Int,
    )

    companion object {

        internal const val FIELD_SCALE = 0.60f
        private const val FIELD_WIDTH = 120.0f
        private const val MAX_LENGTH = 256

    }

}

/**
 * 绘制一行字符串设置；作用域原点即行左上角，所有坐标都是行局部坐标。
 */
fun UiScope.StringSettingRow(row: StringSettingRow, hoverProgress: Float, mouseX: Int, mouseY: Int) {
    val setting = row.getSetting()
    row.textMetrics = metrics
    val labelScale = 0.68f
    val labelY = (height - metrics.textHeight(labelScale)) / 2.0f

    RowSurface(hoverProgress)
    Text(setting.displayName, MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY)

    val fieldBounds = row.localFieldBounds(width)
    // 输入框命中测试用绝对坐标：作用域原点 + 行局部矩形
    val absoluteFieldBounds = fieldBounds.translate(origin.x, origin.y)
    val hovered = absoluteFieldBounds.contains(mouseX.toDouble(), mouseY.toDouble())
    val fieldHover = if (hovered) 1.0f else hoverProgress * 0.55f
    val focused = row.isFocused()

    val displaySource = if (focused) row.getDisplayBuffer() else row.normalize(setting.value)
    val slice = row.buildDisplaySlice(displaySource, fieldBounds, focused)
    var selection: UiTree.SelectionRange? = null
    if (focused && row.hasSelection()) {
        val selectionStartIndex = maxOf(slice.start, row.selectionStart)
        val selectionEndIndex = minOf(slice.end, row.selectionEnd)
        if (selectionEndIndex > selectionStartIndex) {
            selection = UiTree.SelectionRange(selectionStartIndex - slice.start, selectionEndIndex - slice.start)
        }
    }
    Input(
        fieldBounds, focused, fieldHover,
        0.0f, Color(0, 0, 0, 0), 0.0f,
        slice.textX - fieldBounds.x, slice.text, StringSettingRow.FIELD_SCALE, MD3Theme.filledFieldContent(focused),
        selection, if (selection == null) null else {
            MD3Theme.withAlpha(MD3Theme.filledFieldIndicator(focused, fieldHover), 90)
        },
        if (focused) slice.caretIndex else null,
        if (focused) MD3Theme.filledFieldCaret(focused) else null,
        null, 0.0f, null
    )
    if (focused) {
        val caretWidth = metrics.textWidth(
            slice.text.substring(0, minOf(slice.caretIndex, slice.text.length)), StringSettingRow.FIELD_SCALE
        )
        // IME 光标位置要求屏幕/绝对坐标，因此把行局部坐标加回作用域原点
        val caretX = minOf(origin.x + slice.textX + caretWidth, origin.x + fieldBounds.right() - 5.0f)
        val textY = origin.y + fieldBounds.y +
                (fieldBounds.height - metrics.textHeight(StringSettingRow.FIELD_SCALE)) / 2.0f
        IMEFocusHelper.updateCursorPos(caretX, textY)
    }
}
