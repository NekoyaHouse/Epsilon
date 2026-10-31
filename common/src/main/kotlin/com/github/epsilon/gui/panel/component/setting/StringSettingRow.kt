package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.github.epsilon.settings.impl.StringSetting
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Color

class StringSettingRow(private val stringSetting: StringSetting) : SettingRow<StringSetting>(stringSetting) {

    private var textMetrics: TextRenderer? = null
    private var focusedState = false
    private var inputBuffer: String? = null
    private var cursorIndex = 0
    private var selectionAnchor = -1

    override fun buildUi(
        scope: UiTree.Scope, guiGraphics: GuiGraphicsExtractor, textRenderer: TextRenderer,
        bounds: UiRect, hoverProgress: Float, mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        this.textMetrics = textRenderer
        val labelScale = 0.68f
        val labelY = (bounds.height - textRenderer.getHeight(labelScale)) / 2.0f
        scope.roundRect(
            0.0f, 0.0f, bounds.width, bounds.height,
            MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.rowSurface(hoverProgress)
        )
        scope.text(stringSetting.displayName, MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY)

        val fieldBounds = getFieldBounds(bounds)
        val hovered = fieldBounds.contains(mouseX.toDouble(), mouseY.toDouble())
        val fieldHover = if (hovered) 1.0f else hoverProgress * 0.55f

        val displaySource = if (focusedState) getDisplayBuffer() else normalize(stringSetting.value)
        val slice = buildDisplaySlice(displaySource, fieldBounds, focusedState)
        var selection: UiTree.SelectionRange? = null
        if (focusedState && hasSelection()) {
            val selectionStartIndex = maxOf(slice.start, this.selectionStart)
            val selectionEndIndex = minOf(slice.end, this.selectionEnd)
            if (selectionEndIndex > selectionStartIndex) {
                selection = UiTree.SelectionRange(selectionStartIndex - slice.start, selectionEndIndex - slice.start)
            }
        }
        scope.input(
            fieldBounds.relativeTo(bounds), focusedState, fieldHover,
            0.0f, Color(0, 0, 0, 0), 0.0f,
            slice.textX - fieldBounds.x, slice.text, FIELD_SCALE, MD3Theme.filledFieldContent(focusedState),
            selection, if (selection == null) null else {
                MD3Theme.withAlpha(MD3Theme.filledFieldIndicator(focusedState, fieldHover), 90)
            },
            if (focusedState) slice.caretIndex else null,
            if (focusedState) MD3Theme.filledFieldCaret(focusedState) else null,
            null, 0.0f, null
        )
        if (focusedState) {
            var caretX = slice.textX + textRenderer.getWidth(
                slice.text.substring(0, minOf(slice.caretIndex, slice.text.length)), FIELD_SCALE
            )
            caretX = minOf(caretX, fieldBounds.right() - 5.0f)
            val textY = fieldBounds.y + (fieldBounds.height - textRenderer.getHeight(FIELD_SCALE)) / 2.0f
            IMEFocusHelper.updateCursorPos(caretX, textY)
        }
    }

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
        IMEFocusHelper.activate()
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

    private fun getFieldBounds(bounds: UiRect): UiRect = UiRect(
        bounds.right() - MD3Theme.ROW_TRAILING_INSET - FIELD_WIDTH,
        bounds.y + 4.0f,
        FIELD_WIDTH,
        18.0f
    )

    private fun getDisplayBuffer(): String = inputBuffer ?: normalize(stringSetting.value)

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
            val width = metrics.getWidth(slice.text.substring(0, i), FIELD_SCALE)
            if (mouseX <= slice.textX + width) {
                return slice.start + i
            }
        }
        return slice.start + slice.text.length
    }

    private fun buildDisplaySlice(value: String?, fieldBounds: UiRect, editing: Boolean): DisplaySlice {
        val safeValue = value ?: ""
        val horizontalInset = 6.0f
        val availableWidth = maxOf(8.0f, fieldBounds.width - horizontalInset * 2.0f)
        if (!editing) {
            val shown = fitWithEllipsis(safeValue, availableWidth)
            return DisplaySlice(shown, fieldBounds.x + horizontalInset, 0, 0, shown.length)
        }

        val safeCursor = cursorIndex.coerceIn(0, safeValue.length)
        val metrics = textMetrics()

        if (metrics.getWidth(safeValue, FIELD_SCALE) <= availableWidth) {
            return DisplaySlice(safeValue, fieldBounds.x + horizontalInset, safeCursor, 0, safeValue.length)
        }

        var low = 0
        var high = safeCursor
        var bestStart = safeCursor
        while (low <= high) {
            val mid = (low + high) / 2
            val beforeCaret = safeValue.substring(mid, safeCursor)
            if (metrics.getWidth(beforeCaret, FIELD_SCALE) <= availableWidth - 2.0f) {
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
            if (metrics.getWidth(candidate, FIELD_SCALE) <= availableWidth) {
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
        if (metrics.getWidth(value, FIELD_SCALE) <= availableWidth) {
            return value
        }
        val ellipsis = "..."
        val ellipsisWidth = metrics.getWidth(ellipsis, FIELD_SCALE)
        if (ellipsisWidth >= availableWidth) {
            return ""
        }
        var low = 0
        var high = value.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            val candidate = value.substring(0, mid) + ellipsis
            if (metrics.getWidth(candidate, FIELD_SCALE) <= availableWidth) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        return value.substring(0, low) + ellipsis
    }

    private fun normalize(value: String?): String = value ?: ""

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

    private fun hasSelection(): Boolean = selectionAnchor >= 0 && selectionAnchor != cursorIndex

    private val selectionStart: Int
        get() = minOf(selectionAnchor, cursorIndex)

    private val selectionEnd: Int
        get() = maxOf(selectionAnchor, cursorIndex)

    private fun clearSelection() {
        selectionAnchor = -1
    }

    private fun isControlDown(): Boolean =
        InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL)

    private fun textMetrics(): TextRenderer = textMetrics ?: FALLBACK_TEXT_METRICS

    private data class DisplaySlice(
        val text: String,
        val textX: Float,
        val caretIndex: Int,
        val start: Int,
        val end: Int,
    )

    companion object {

        private const val FIELD_SCALE = 0.60f
        private const val FIELD_WIDTH = 120.0f
        private const val MAX_LENGTH = 256

    }

}
