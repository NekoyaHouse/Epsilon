package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.gui.dropdown.widget.SettingWidget
import com.github.epsilon.gui.lib.UiScope
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

    override fun UiScope.drawPanelContent(mouseX: Int, mouseY: Int, visibleHeight: Float) {
        settingsContent.draw(
            this, mouseX, mouseY, contentLocalTop(origin) - scroll,
            width, getRenderFrameId()
        )
    }


    companion object {

        fun createWidget(setting: Setting<*>): SettingWidget<*>? {
            return SettingsContent.createWidget(setting)
        }

    }

}
