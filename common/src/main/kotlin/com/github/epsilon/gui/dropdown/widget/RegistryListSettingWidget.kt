package com.github.epsilon.gui.dropdown.widget

import com.github.epsilon.Constants
import com.github.epsilon.gui.dropdown.ListSettingPopupScreen
import com.github.epsilon.gui.utils.RegistryListUi
import com.github.epsilon.settings.impl.RegistryListSetting

class RegistryListSettingWidget(setting: RegistryListSetting<*>) :
    AbstractSetSettingWidget<RegistryListSetting<*>>(setting) {

    override fun elementCount(): Int {
        return setting.size()
    }

    override fun labelText(): String {
        return RegistryListUi.labelText(setting.registryType)
    }

    override fun openPopup() {
        val screen = Constants.client.gui.screen()
        if (screen is ListSettingPopupScreen) {
            screen.openRegistryListSettingPopup(setting)
        }
    }

}
