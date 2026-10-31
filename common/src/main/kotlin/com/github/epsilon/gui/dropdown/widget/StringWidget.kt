package com.github.epsilon.gui.dropdown.widget

import com.github.epsilon.gui.dropdown.DropdownScreen
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.settings.impl.StringSetting
import com.mojang.blaze3d.platform.InputConstants

class StringWidget(setting: StringSetting) : SettingWidget<StringSetting>(setting) {

    private val inputField: DropdownTextField = DropdownTextField(MAX_LENGTH)

    override fun getHeight(): Float {
        return DropdownTheme.SETTING_HEIGHT + DropdownTheme.INPUT_HEIGHT + 2.0f
    }

    override fun draw(scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int) {
        scope.text(
            setting.displayName,
            DropdownTheme.SETTING_PADDING_X,
            1.0f,
            DropdownTheme.SETTING_TEXT_SCALE,
            DropdownTheme.settingLabel()
        )

        val fieldX = DropdownTheme.SETTING_PADDING_X
        val fieldY = DropdownTheme.SETTING_HEIGHT
        val fieldW = width - DropdownTheme.SETTING_PADDING_X * 2.0f
        val fieldH = DropdownTheme.INPUT_HEIGHT

        if (!inputField.isFocused && inputField.text != setting.value) {
            inputField.text = setting.value
        }
        inputField.draw(
            scope,
            textMetrics,
            fieldX,
            fieldY,
            fieldW,
            fieldH,
            mouseX,
            mouseY,
            "...",
            DropdownTheme.SETTING_TEXT_SCALE
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val fieldX = absoluteX(DropdownTheme.SETTING_PADDING_X)
        val fieldY = absoluteY(DropdownTheme.SETTING_HEIGHT)
        val fieldW = width - DropdownTheme.SETTING_PADDING_X * 2.0f
        val fieldH = DropdownTheme.INPUT_HEIGHT

        if (button == InputConstants.MOUSE_BUTTON_LEFT && isHovered(mouseX, mouseY, fieldX, fieldY, fieldW, fieldH)) {
            if (!inputField.isFocused) {
                inputField.text = setting.value
            }
            inputField.focusIfContains(mouseX, mouseY, fieldX, fieldY, fieldW, fieldH)
            DropdownScreen.react(ReisaDropdownCompanion.Action.TYPING)
            return true
        }
        if (inputField.isFocused) {
            commitSetting()
            inputField.blur()
        }
        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!inputField.isFocused) return false

        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            commitSetting()
            inputField.blur()
            DropdownScreen.react(ReisaDropdownCompanion.Action.CONFIRM)
            return true
        }
        if (keyCode == InputConstants.KEY_ESCAPE) {
            inputField.text = setting.value
            inputField.blur()
            DropdownScreen.react(ReisaDropdownCompanion.Action.CANCEL)
            return true
        }
        if (inputField.keyPressed(keyCode)) {
            previewSetting()
            return true
        }
        return false
    }

    override fun charTyped(typedText: String): Boolean {
        if (inputField.charTyped(typedText)) {
            previewSetting()
            return true
        }
        return false
    }

    fun isFocused(): Boolean {
        return inputField.isFocused
    }

    fun blurInput() {
        commitSetting()
        inputField.blur()
    }

    private fun previewSetting() {
        if (!setting.isApplyWhenRelease) {
            applyText(inputField.text)
        }
    }

    private fun commitSetting() {
        applyText(inputField.text)
    }

    private fun applyText(value: String) {
        if (setting.value != value) {
            setting.value = value
        }
    }

    companion object {
        /**
         * 与 Panel 的 `StringSettingRow` 保持一致；过短会把 URL、字体路径一类的长值截断后写回配置。
         */
        private const val MAX_LENGTH = 256
    }

}
