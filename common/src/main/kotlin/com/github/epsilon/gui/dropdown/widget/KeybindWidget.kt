package com.github.epsilon.gui.dropdown.widget

import com.github.epsilon.gui.dropdown.DropdownScreen
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.impl.KeybindSetting
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants

class KeybindWidget(setting: KeybindSetting) : SettingWidget<KeybindSetting>(setting) {

    private val hoverAnim: Animation = Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER)
    private var listening: Boolean = false
    private var buttonX: Float = 0.0f
    private var buttonY: Float = 0.0f
    private var buttonW: Float = DropdownTheme.KEYBIND_WIDTH
    private var buttonH: Float = DropdownTheme.KEYBIND_HEIGHT

    override fun getHeight(): Float {
        return DropdownTheme.SETTING_HEIGHT
    }

    override fun draw(scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int) {
        val lineHeight = textMetrics.textHeight(DropdownTheme.SETTING_TEXT_SCALE)
        val labelTextY = (getHeight() - lineHeight) * 0.5f
        scope.text(setting.displayName, DropdownTheme.SETTING_PADDING_X, labelTextY, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel())

        val keyText = if (listening) "..." else KeybindUtils.format(setting.value)
        val textW = textMetrics.textWidth(keyText, DropdownTheme.SETTING_TEXT_SCALE)
        buttonW = Math.max(DropdownTheme.KEYBIND_WIDTH, textW + 8.0f)
        buttonH = DropdownTheme.KEYBIND_HEIGHT
        val localButtonX = width - DropdownTheme.SETTING_PADDING_X - buttonW
        val localButtonY = labelTextY + (lineHeight - buttonH) * 0.5f
        buttonX = absoluteX(localButtonX)
        buttonY = absoluteY(localButtonY)

        val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), buttonX - 2.0f, buttonY - 2.0f, buttonW + 4.0f, buttonH + 4.0f)
        hoverAnim.run(if (hovered || listening) 1.0f else 0.0f)

        val outline = if (listening) {
            MD3Theme.withAlpha(MD3Theme.PRIMARY, 200)
        } else {
            MD3Theme.lerp(MD3Theme.withAlpha(MD3Theme.OUTLINE, 96), MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, 136), hoverAnim.value * 0.55f)
        }

        scope.roundRect(localButtonX, localButtonY, buttonW, buttonH, DropdownTheme.KEYBIND_RADIUS, DropdownTheme.keybindSurface(listening))
        scope.outline(localButtonX, localButtonY, buttonW, buttonH, DropdownTheme.KEYBIND_RADIUS, 0.7f, outline)
        scope.text(keyText, localButtonX + (buttonW - textW) * 0.5f, labelTextY, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.keybindText(listening))
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == InputConstants.MOUSE_BUTTON_LEFT && isHovered(mouseX, mouseY, buttonX, buttonY, buttonW, buttonH)) {
            listening = !listening
            DropdownScreen.react(
                if (listening) ReisaDropdownCompanion.Action.KEY_BIND else ReisaDropdownCompanion.Action.CANCEL
            )
            return true
        }

        if (listening && button != InputConstants.MOUSE_BUTTON_LEFT) {
            setting.value = KeybindUtils.encodeMouseButton(button)
            listening = false
            DropdownScreen.react(ReisaDropdownCompanion.Action.CONFIRM)
            return true
        }
        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!listening) return false

        if (keyCode == InputConstants.KEY_ESCAPE) {
            setting.value = KeybindUtils.NONE
        } else if (keyCode == InputConstants.KEY_BACKSPACE) {
            setting.value = KeybindUtils.NONE
        } else {
            setting.value = keyCode
        }
        listening = false
        DropdownScreen.react(
            if (keyCode == InputConstants.KEY_ESCAPE) ReisaDropdownCompanion.Action.CANCEL else ReisaDropdownCompanion.Action.CONFIRM
        )
        return true
    }

    fun isListening(): Boolean {
        return listening
    }

}
