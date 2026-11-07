package com.github.epsilon.gui.panel.view.settings

import com.github.epsilon.kernel.state.*
import com.github.epsilon.gui.lib.Input
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.animate
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import java.awt.Color

class ClientSettingTextField(private val maxLength: Int) {

    // 绘制入口是与本类同文件的 [UiScope.ClientSettingTextField]，状态需要对该扩展函数可见。
    internal val hoverAnimation = Animation(Easing.EASE_OUT_CUBIC, HOVER_DURATION)

    internal val focusAnimation = Animation(Easing.EASE_OUT_CUBIC, HOVER_DURATION)

    internal var focused by mutableStateOf(false)

    internal var text by mutableStateOf("")

    internal var cursor by mutableStateOf(0)

    init {
        hoverAnimation.startValue = 0.0f
        focusAnimation.startValue = 0.0f
    }

    fun focusIfContains(bounds: UiRect, mouseX: Double, mouseY: Double): Boolean {
        if (!bounds.contains(mouseX, mouseY)) {
            return false
        }
        focus()
        return true
    }

    fun focus() {
        if (focused) return
        focused = true
        cursor = text.length
        IMEFocusHelper.activate()
    }

    fun blur() {
        if (focused) {
            focused = false
            IMEFocusHelper.deactivate()
        }
    }

    fun keyPressed(event: KeyEvent): Boolean {
        if (!focused) {
            return false
        }

        if (isControlDown()) {
            return handleControlShortcut(event.key())
        }

        return when (event.key()) {
            InputConstants.KEY_BACKSPACE -> {
                if (cursor > 0 && !text.isEmpty()) {
                    text = text.substring(0, cursor - 1) + text.substring(cursor)
                    cursor--
                }
                true
            }

            InputConstants.KEY_DELETE -> {
                if (cursor < text.length) {
                    text = text.substring(0, cursor) + text.substring(cursor + 1)
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
        if (!focused) {
            return false
        }
        val typed = event.codepointAsString()
        if (typed.isEmpty()) {
            return true
        }
        insertText(typed)
        return true
    }

    fun isFocused(): Boolean {
        return focused
    }

    fun getText(): String {
        return text
    }

    fun setText(text: String?) {
        this.text = text?.let { clampToMaxLength(it) } ?: ""
        this.cursor = minOf(this.cursor, this.text.length)
    }

    fun clear() {
        text = ""
        cursor = 0
    }

    fun setCursorToEnd() {
        cursor = text.length
    }

    private fun handleControlShortcut(key: Int): Boolean {
        return when (key) {
            InputConstants.KEY_A -> {
                cursor = text.length
                true
            }

            InputConstants.KEY_V -> {
                val clipboard = mc.keyboardHandler.clipboard
                if (!clipboard.isEmpty()) {
                    val sanitized = clipboard.codePoints()
                        .filter { codepoint -> codepoint >= 32 && codepoint != 127 }
                        .collect(
                            { StringBuilder() },
                            { builder, codepoint -> builder.appendCodePoint(codepoint) },
                            { left, right -> left.append(right) }
                        )
                        .toString()
                    if (!sanitized.isEmpty()) {
                        insertText(sanitized)
                    }
                }
                true
            }

            else -> false
        }
    }

    private fun insertText(inserted: String?) {
        if (inserted == null || inserted.isEmpty()) {
            return
        }
        val available = maxLength - text.length
        if (available <= 0) {
            return
        }
        val safeInsert = if (inserted.length > available) inserted.substring(0, available) else inserted
        text = text.substring(0, cursor) + safeInsert + text.substring(cursor)
        cursor += safeInsert.length
    }

    private fun clampToMaxLength(value: String): String {
        return if (value.length > maxLength) value.substring(0, maxLength) else value
    }

    private fun isControlDown(): Boolean {
        return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL)
    }

    private companion object {

        private const val HOVER_DURATION = 120L

    }

}

/**
 * 绘制一个客户端设置输入框；作用域原点即输入框左上角，所有坐标都是局部坐标。
 *
 * 命中测试仍由调用方用绝对矩形完成，[UiScope.origin] 只用于换算 IME 光标等需要绝对坐标的位置。
 */
fun UiScope.ClientSettingTextField(
    field: ClientSettingTextField, mouseX: Int, mouseY: Int,
    placeholder: String, textScale: Float, trailingHint: String?,
) {
    val hovered = origin.contains(mouseX.toDouble(), mouseY.toDouble())
    animate(field.hoverAnimation, hovered)
    val focusProgress = animate(field.focusAnimation, field.focused)
    val textInset = 10.0f
    val textHeight = metrics.textHeight(textScale)
    val textX = textInset
    val textY = (height - textHeight) / 2.0f

    val showPlaceholder = field.text.isEmpty() && !field.focused
    val display = if (showPlaceholder) placeholder else field.text
    val textColor: Color = if (showPlaceholder) MD3Theme.TEXT_MUTED else MD3Theme.TEXT_PRIMARY
    val showTrailing = field.focused && trailingHint != null && !trailingHint.isBlank() && !field.text.isEmpty()
    Input(
        UiRect(0.0f, 0.0f, width, height), field.focused, if (hovered) 0.6f else 0.0f,
        focusProgress, MD3Theme.PRIMARY, 1.0f,
        textInset, display, textScale, textColor,
        null, null,
        if (field.focused) minOf(field.cursor, field.text.length) else null,
        if (field.focused) MD3Theme.TEXT_PRIMARY else null,
        if (showTrailing) trailingHint else null,
        0.56f,
        if (showTrailing) MD3Theme.TEXT_MUTED else null
    )

    if (field.focused) {
        val safeCursor = minOf(field.cursor, field.text.length)
        // IME 光标位置需要绝对坐标：行局部坐标加回作用域原点
        val caretX = origin.x + textX + metrics.textWidth(field.text.substring(0, safeCursor), textScale)
        IMEFocusHelper.updateCursorPos(caretX, origin.y + textY)
    }
}
