package com.github.epsilon.gui.dropdown.widget

import com.github.epsilon.gui.dropdown.DropdownScreen
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.settings.impl.ButtonSetting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants

class ButtonWidget(setting: ButtonSetting) : SettingWidget<ButtonSetting>(setting) {

    private val hoverAnim: Animation = Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER)

    override fun getHeight(): Float {
        return DropdownTheme.SETTING_HEIGHT + 2.0f
    }

    override fun draw(scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int) {
        val btnX = DropdownTheme.SETTING_PADDING_X
        val btnY = 1.0f
        val btnW = width - DropdownTheme.SETTING_PADDING_X * 2.0f
        val btnH = DropdownTheme.BUTTON_HEIGHT

        val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), absoluteX(btnX), absoluteY(btnY), btnW, btnH)
        hoverAnim.run(if (hovered) 1.0f else 0.0f)

        scope.roundRect(btnX, btnY, btnW, btnH, DropdownTheme.BUTTON_RADIUS, DropdownTheme.buttonSurface(hoverAnim.value))

        val label = setting.displayName
        val textW = textMetrics.textWidth(label, DropdownTheme.SETTING_TEXT_SCALE)
        val textY = btnY + (btnH - textMetrics.textHeight(DropdownTheme.SETTING_TEXT_SCALE)) * 0.5f
        scope.text(label, btnX + (btnW - textW) * 0.5f, textY, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.buttonText())
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return false

        val btnX = absoluteX(DropdownTheme.SETTING_PADDING_X)
        val btnY = absoluteY(1.0f)
        val btnW = width - DropdownTheme.SETTING_PADDING_X * 2.0f
        val btnH = DropdownTheme.BUTTON_HEIGHT

        if (isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
            val action = setting.value
            if (action != null) {
                action.run()
            }
            DropdownScreen.react(ReisaDropdownCompanion.Action.BUTTON_ACTION)
            return true
        }
        return false
    }

}
