package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.widget.*
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.impl.*

class SettingsContent(ownerKey: String, settings: List<Setting<*>>) {

    private val renderer: SettingSectionRenderer =
        SettingSectionRenderer.create(ownerKey, settings) { setting -> createWidget(setting) }

    private var cachedContentHeightFrameId: Int = Int.MAX_VALUE
    private var cachedContentHeight: Float = 0.0f

    fun computeContentHeight(): Float {
        if (renderer.isEmpty()) {
            return DropdownTheme.MODULE_HEIGHT
        }
        return DropdownTheme.SETTING_GAP + renderer.height()
    }

    fun computeContentHeight(frameId: Int): Float {
        if (frameId == Int.MIN_VALUE) {
            return computeContentHeight()
        }
        if (cachedContentHeightFrameId != frameId) {
            cachedContentHeight = computeContentHeight()
            cachedContentHeightFrameId = frameId
        }
        return cachedContentHeight
    }

    fun draw(
        scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int,
        panelX: Float, contentY: Float, panelWidth: Float,
    ) {
        draw(scope, textMetrics, mouseX, mouseY, panelX, contentY, panelWidth, Int.MIN_VALUE)
    }

    fun draw(
        scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int,
        panelX: Float, contentY: Float, panelWidth: Float, frameId: Int,
    ) {
        if (renderer.isEmpty()) {
            val label = EpsilonTranslations.Gui.NO_SETTINGS.getTranslatedName()
            val labelScale = 0.58f
            val textW = textMetrics.textWidth(label, labelScale)
            scope.text(label, panelX + (panelWidth - textW) * 0.5f, contentY + 8.0f, labelScale, MD3Theme.TEXT_MUTED)
            return
        }

        if (frameId != Int.MIN_VALUE) {
            computeContentHeight(frameId)
        }
        renderer.draw(
            scope, textMetrics, mouseX, mouseY, panelX, contentY + DropdownTheme.SETTING_GAP,
            panelWidth, 0.0f, 0.0f
        )
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int, panelX: Float, contentY: Float, panelWidth: Float): Boolean {
        if (renderer.mouseClicked(
                mouseX, mouseY, button, panelX, contentY + DropdownTheme.SETTING_GAP,
                panelWidth, 0.0f, 0.0f
            )
        ) {
            return true
        }
        renderer.blurAllInputs()
        return false
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int, panelX: Float, contentY: Float, panelWidth: Float): Boolean {
        return renderer.mouseReleased(mouseX, mouseY, button)
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return renderer.keyPressed(keyCode, scanCode, modifiers)
    }

    fun charTyped(typedText: String): Boolean {
        return renderer.charTyped(typedText)
    }

    fun hasActiveInput(): Boolean {
        return renderer.hasActiveInput()
    }

    companion object {

        fun createWidget(setting: Setting<*>): SettingWidget<*>? {
            if (setting is BoolSetting) return BoolWidget(setting)
            if (setting is IntSetting) return IntSliderWidget(setting)
            if (setting is DoubleSetting) return DoubleSliderWidget(setting)
            if (setting is EnumSetting<*>) return EnumWidget(setting)
            if (setting is ColorSetting) return ColorWidget(setting)
            if (setting is RegistryListSetting<*>) return RegistryListSettingWidget(setting)
            if (setting is KeybindSetting) return KeybindWidget(setting)
            if (setting is StringSetting) return StringWidget(setting)
            if (setting is ButtonSetting) return ButtonWidget(setting)
            if (setting is StringListSetting) return StringListSettingWidget(setting)
            return null
        }
    }

}
