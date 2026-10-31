package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.widget.SettingWidget
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.settings.Setting

class SettingsListPanel : AbstractDropdownPanel {

    private val settingsContent: SettingsContent

    constructor(id: String, title: String, icon: String?, panelIndex: Int, settings: List<Setting<*>>) :
            super(id, title, icon, panelIndex) {
        this.settingsContent = SettingsContent("dropdown-panel:$id", settings)
    }

    constructor(
        id: String,
        titleComponent: TranslateComponent,
        icon: String?,
        panelIndex: Int,
        settings: List<Setting<*>>
    ) :
            super(id, titleComponent, icon, panelIndex) {
        this.settingsContent = SettingsContent("dropdown-panel:$id", settings)
    }

    override fun computeContentHeight(): Float {
        return settingsContent.computeContentHeight(getRenderFrameId())
    }

    override fun drawPanelContent(
        scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int, visibleHeight: Float,
    ) {
        settingsContent.draw(
            scope, textMetrics, mouseX, mouseY, x, y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll,
            width, getRenderFrameId()
        )
    }

    override fun mouseClickedContent(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return settingsContent.mouseClicked(
            mouseX,
            mouseY,
            button,
            x,
            y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll,
            width
        )
    }

    override fun mouseReleasedContent(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return settingsContent.mouseReleased(
            mouseX,
            mouseY,
            button,
            x,
            y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll,
            width
        )
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return settingsContent.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(typedText: String): Boolean {
        return settingsContent.charTyped(typedText)
    }

    override fun hasActiveInput(): Boolean {
        return settingsContent.hasActiveInput()
    }

    companion object {

        fun createWidget(setting: Setting<*>): SettingWidget<*>? {
            return SettingsContent.createWidget(setting)
        }
    }

}
