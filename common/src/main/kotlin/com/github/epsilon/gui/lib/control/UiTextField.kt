package com.github.epsilon.gui.lib.control

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.github.epsilon.kernel.render.animation.Easing
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import java.awt.Color
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation

class UiTextField(maxLength: Int) {

    private val hoverAnimation: Animation = Animation(Easing.EASE_OUT_CUBIC, ANIM_DURATION)
    private val focusAnimation: Animation = Animation(Easing.EASE_OUT_CUBIC, ANIM_DURATION)

    private var maxLength: Int
    private var focused by mutableStateOf(false)
    private var textStore by mutableStateOf("")
    private var cursor by mutableStateOf(0)

    private var leadingIconGlyph: String? = null
    private var leadingIconScale = 1.0f

    init {
        this.maxLength = maxLength
        hoverAnimation.startValue = 0.0f
        focusAnimation.startValue = 0.0f
    }

    fun leadingIcon(glyph: String, scale: Float): UiTextField {
        this.leadingIconGlyph = glyph
        this.leadingIconScale = scale
        return this
    }

    fun buildUi(
        scope: UiScope, bounds: UiRect, mouseX: Double, mouseY: Double,
        textRenderer: TextRenderer, placeholder: String, textScale: Float
    ) {
        val hovered = bounds.contains(mouseX, mouseY)
        val hoverProgress = scope.animate(hoverAnimation, hovered)
        val focusProgress = scope.animate(focusAnimation, focused)

        val leadingIcon = leadingIconGlyph
        val iconWidth = if (leadingIcon == null) {
            0.0f
        } else {
            textRenderer.getWidth(leadingIcon, leadingIconScale, StaticFontLoader.ICONS) + ICON_GAP
        }
        val textInset = DEFAULT_TEXT_INSET + iconWidth

        val textHeight = textRenderer.getHeight(textScale)
        val textX = bounds.x + textInset
        val textY = bounds.y + (bounds.height - textHeight) / 2.0f

        val showPlaceholder = textStore.isEmpty() && !focused
        val display = if (showPlaceholder) placeholder else textStore
        val textColor = if (showPlaceholder) MD3Theme.TEXT_MUTED else MD3Theme.TEXT_PRIMARY

        scope.Input(
            bounds, focused, hoverProgress,
            focusProgress, MD3Theme.PRIMARY, 1.0f,
            textInset, display, textScale, textColor,
            null, null,
            if (focused) safeCursor() else null, if (focused) MD3Theme.TEXT_PRIMARY else null,
            null, 0.0f, null
        )

        if (leadingIcon != null) {
            val glyphHeight = textRenderer.getHeight(leadingIconScale, StaticFontLoader.ICONS)
            val iconColor: Color = if (focused) MD3Theme.PRIMARY else MD3Theme.TEXT_MUTED
            scope.Text(
                leadingIcon, bounds.x + DEFAULT_TEXT_INSET,
                bounds.y + (bounds.height - glyphHeight) / 2.0f, leadingIconScale, iconColor, StaticFontLoader.ICONS
            )
        }

        if (focused) {
            val caretX = textX + textRenderer.getWidth(textStore.substring(0, safeCursor()), textScale)
            IMEFocusHelper.updateCursorPos(caretX, textY)
        }
    }

    fun focusIfContains(bounds: UiRect?, mouseX: Double, mouseY: Double): Boolean {
        if (bounds == null || !bounds.contains(mouseX, mouseY)) {
            return false
        }
        focus()
        return true
    }

    fun focus() {
        if (focused) return
        focused = true
        cursor = textStore.length
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
                if (cursor > 0 && !textStore.isEmpty()) {
                    textStore = textStore.substring(0, cursor - 1) + textStore.substring(cursor)
                    cursor--
                }
                true
            }

            InputConstants.KEY_DELETE -> {
                if (cursor < textStore.length) {
                    textStore = textStore.substring(0, cursor) + textStore.substring(cursor + 1)
                }
                true
            }

            InputConstants.KEY_LEFT -> {
                cursor = maxOf(0, cursor - 1)
                true
            }

            InputConstants.KEY_RIGHT -> {
                cursor = minOf(textStore.length, cursor + 1)
                true
            }

            InputConstants.KEY_HOME -> {
                cursor = 0
                true
            }

            InputConstants.KEY_END -> {
                cursor = textStore.length
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

    fun isEmpty(): Boolean {
        return textStore.isEmpty()
    }

    fun getText(): String {
        return textStore
    }

    fun setMaxLength(maxLength: Int) {
        this.maxLength = maxLength
        if (textStore.length > maxLength) {
            textStore = textStore.substring(0, maxLength)
        }
        cursor = minOf(cursor, textStore.length)
    }

    fun clear() {
        textStore = ""
        cursor = 0
    }

    private fun safeCursor(): Int {
        return (cursor.toLong()).coerceIn(((0).toLong()).toLong(), ((textStore.length).toLong()).toLong()).toInt()
            .toInt()
    }

    private fun handleControlShortcut(key: Int): Boolean {
        return when (key) {
            InputConstants.KEY_A -> {
                cursor = textStore.length
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
        val available = maxLength - textStore.length
        if (available <= 0) {
            return
        }
        val safeInsert = if (inserted.length > available) inserted.substring(0, available) else inserted
        textStore = textStore.substring(0, cursor) + safeInsert + textStore.substring(cursor)
        cursor += safeInsert.length
    }

    private fun isControlDown(): Boolean {
        return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL)
    }

    private companion object {

        private const val ANIM_DURATION = 120L
        private const val DEFAULT_TEXT_INSET = 10.0f
        private const val ICON_GAP = 6.0f

    }

}
