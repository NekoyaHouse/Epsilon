package com.github.epsilon.gui.dropdown.widget

import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import java.util.function.Predicate

class DropdownTextField(private val maxLength: Int, inputFilter: Predicate<String>?) {

    constructor(maxLength: Int) : this(maxLength, Predicate { true })

    private val inputFilter: Predicate<String> = inputFilter ?: Predicate { true }

    var isFocused: Boolean = false
        private set

    var text: String = ""
        set(value) {
            field = clamp(value)
            cursor = cursor.coerceIn(0, field.length)
        }

    private var cursor: Int = 0
    private var cursorMidpoints: FloatArray = FloatArray(0)

    private val safeCursor: Int
        get() = cursor.coerceIn(0, text.length)

    fun draw(
        scope: UiTree.Scope, textMetrics: UiTextMetrics, x: Float, y: Float, width: Float, height: Float,
        mouseX: Int, mouseY: Int, placeholder: String, textScale: Float,
    ) {
        draw(scope, textMetrics, x, y, width, height, mouseX, mouseY, placeholder, textScale, false)
    }

    fun draw(
        scope: UiTree.Scope, textMetrics: UiTextMetrics, x: Float, y: Float, width: Float, height: Float,
        mouseX: Int, mouseY: Int, placeholder: String, textScale: Float, textShadow: Boolean,
    ) {
        scope.roundRect(x, y, width, height, DropdownTheme.INPUT_RADIUS, DropdownTheme.inputSurface(isFocused))
        scope.outline(
            x,
            y,
            width,
            height,
            DropdownTheme.INPUT_RADIUS,
            0.7f,
            if (isFocused) MD3Theme.PRIMARY else MD3Theme.withAlpha(MD3Theme.OUTLINE, 90)
        )

        val showPlaceholder = text.isEmpty() && !isFocused
        val display = if (showPlaceholder) placeholder else text
        val textY = y + (height - textMetrics.textHeight(textScale)) / 2.0f
        val textX = x + 4.0f
        updateCursorLayout(textMetrics, textX, textScale)
        val visibleText = trimToWidth(display, textScale, width - 8.0f, textMetrics)
        val textColor = if (showPlaceholder) MD3Theme.TEXT_MUTED else MD3Theme.TEXT_PRIMARY
        if (textShadow) {
            scope.textWithShadow(visibleText, textX, textY, textScale, textColor)
        } else {
            scope.text(visibleText, textX, textY, textScale, textColor)
        }

        if (isFocused) {
            val safeCursor = cursor.coerceIn(0, text.length)
            val caretX = textX + textMetrics.textWidth(text.substring(0, safeCursor), textScale)
            drawCaret(scope, textMetrics, caretX, textY, textScale)
            IMEFocusHelper.updateCursorPos(caretX, textY)
        }
    }

    fun drawCentered(
        scope: UiTree.Scope, textMetrics: UiTextMetrics, x: Float, y: Float, width: Float, height: Float,
        mouseX: Int, mouseY: Int, placeholder: String, textScale: Float,
    ) {
        scope.roundRect(x, y, width, height, DropdownTheme.INPUT_RADIUS, DropdownTheme.inputSurface(isFocused))
        scope.outline(
            x,
            y,
            width,
            height,
            DropdownTheme.INPUT_RADIUS,
            0.7f,
            if (isFocused) MD3Theme.PRIMARY else MD3Theme.withAlpha(MD3Theme.OUTLINE, 90)
        )

        val showPlaceholder = text.isEmpty() && !isFocused
        val display = if (showPlaceholder) placeholder else text

        val textY = y + (height - textMetrics.textHeight(textScale)) / 2.0f
        val visibleText = trimToWidth(display, textScale, width - 8.0f, textMetrics)
        val textX = x + (width - textMetrics.textWidth(visibleText, textScale)) * 0.5f
        val caretBaseX = x + (width - textMetrics.textWidth(text, textScale)) * 0.5f
        updateCursorLayout(textMetrics, caretBaseX, textScale)
        scope.text(
            visibleText,
            textX,
            textY,
            textScale,
            if (showPlaceholder) MD3Theme.TEXT_MUTED else MD3Theme.TEXT_PRIMARY
        )

        if (isFocused) {
            val safeCursor = cursor.coerceIn(0, text.length)
            val beforeCursor = text.substring(0, safeCursor)
            val caretX = caretBaseX + textMetrics.textWidth(beforeCursor, textScale)
            drawCaret(scope, textMetrics, caretX, textY, textScale)
            IMEFocusHelper.updateCursorPos(caretX, textY)
        }
    }

    fun focusIfContains(mouseX: Double, mouseY: Double, x: Float, y: Float, width: Float, height: Float): Boolean {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false
        }
        isFocused = true
        cursor = resolveCursor(mouseX).coerceIn(0, text.length)
        IMEFocusHelper.activate()
        return true
    }

    fun focusIfContainsCentered(
        mouseX: Double,
        mouseY: Double,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ): Boolean {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false
        }
        isFocused = true
        cursor = resolveCursor(mouseX).coerceIn(0, text.length)
        IMEFocusHelper.activate()
        return true
    }

    fun focus() {
        isFocused = true
        cursor = text.length
        IMEFocusHelper.activate()
    }

    fun blur() {
        if (isFocused) {
            isFocused = false
            IMEFocusHelper.deactivate()
        }
    }

    fun keyPressed(event: KeyEvent): Boolean {
        return keyPressed(event.key())
    }

    fun keyPressed(keyCode: Int): Boolean {
        if (!isFocused) return false
        if (isControlDown()) {
            return handleControlShortcut(keyCode)
        }
        return when (keyCode) {
            InputConstants.KEY_BACKSPACE -> {
                val index = safeCursor
                if (index > 0) {
                    text = text.substring(0, index - 1) + text.substring(index)
                    cursor = index - 1
                }
                true
            }

            InputConstants.KEY_DELETE -> {
                val index = safeCursor
                if (index < text.length) {
                    text = text.substring(0, index) + text.substring(index + 1)
                    cursor = index
                }
                true
            }

            InputConstants.KEY_LEFT -> {
                cursor = maxOf(0, cursor - 1)
                true
            }

            InputConstants.KEY_RIGHT -> {
                cursor = minOf(text.length, cursor + 1)
                true
            }

            InputConstants.KEY_HOME -> {
                cursor = 0
                true
            }

            InputConstants.KEY_END -> {
                cursor = text.length
                true
            }

            else -> false
        }
    }

    fun charTyped(event: CharacterEvent): Boolean {
        return isFocused && insertText(event.codepointAsString())
    }

    fun charTyped(typedText: String): Boolean {
        return isFocused && insertText(typedText)
    }

    fun getCursorIndex(): Int = safeCursor

    fun clear() {
        text = ""
        cursor = 0
    }

    fun setCursorToEnd() {
        cursor = text.length
    }

    private fun insertText(introduced: String?): Boolean {
        if (introduced == null || introduced.isEmpty()) return false
        val accepted = StringBuilder()
        introduced.codePoints().forEach { codePoint ->
            val candidate = String(Character.toChars(codePoint))
            if (inputFilter.test(candidate)) accepted.append(candidate)
        }
        if (accepted.isEmpty()) return false
        val available = maxLength - text.length
        if (available <= 0) return false
        val safe = if (accepted.length > available) accepted.substring(0, available) else accepted.toString()
        if (safe.isEmpty()) return false
        val index = safeCursor
        text = text.substring(0, index) + safe + text.substring(index)
        cursor = index + safe.length
        return true
    }

    private fun handleControlShortcut(keyCode: Int): Boolean {
        return when (keyCode) {
            InputConstants.KEY_A -> {
                cursor = text.length
                true
            }

            InputConstants.KEY_V -> insertText(mc.keyboardHandler.clipboard)
            else -> false
        }
    }

    private fun isControlDown(): Boolean {
        val window = mc.window
        return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL)
    }

    private fun clamp(value: String): String {
        return if (value.length > maxLength) value.substring(0, maxLength) else value
    }

    private fun updateCursorLayout(textMetrics: UiTextMetrics, textX: Float, textScale: Float) {
        if (text.isEmpty()) {
            cursorMidpoints = FloatArray(0)
            return
        }
        if (cursorMidpoints.size != text.length) {
            cursorMidpoints = FloatArray(text.length)
        }
        var left = 0.0f
        for (i in 0 until text.length) {
            val right = textMetrics.textWidth(text.substring(0, i + 1), textScale)
            cursorMidpoints[i] = textX + (left + right) * 0.5f
            left = right
        }
    }

    private fun resolveCursor(mouseX: Double): Int {
        if (text.isEmpty()) return 0
        if (cursorMidpoints.size != text.length) return text.length
        for (i in cursorMidpoints.indices) {
            if (mouseX < cursorMidpoints[i]) return i
        }
        return text.length
    }

    private fun drawCaret(scope: UiTree.Scope, textMetrics: UiTextMetrics, x: Float, y: Float, textScale: Float) {
        if (System.currentTimeMillis() % 1000 > 500) {
            scope.rect(x, y, 0.8f, textMetrics.textHeight(textScale), MD3Theme.TEXT_PRIMARY)
        }
    }

    private fun trimToWidth(value: String?, scale: Float, maxWidth: Float, textMetrics: UiTextMetrics): String {
        if (value == null || value.isEmpty()) return ""
        if (textMetrics.textWidth(value, scale) <= maxWidth) return value
        val ellipsis = "..."
        val ellipsisWidth = textMetrics.textWidth(ellipsis, scale)
        if (ellipsisWidth >= maxWidth) return ellipsis
        var low = 0
        var high = value.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            val candidate = value.substring(0, mid) + ellipsis
            if (textMetrics.textWidth(candidate, scale) <= maxWidth) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        return value.substring(0, low) + ellipsis
    }

}
