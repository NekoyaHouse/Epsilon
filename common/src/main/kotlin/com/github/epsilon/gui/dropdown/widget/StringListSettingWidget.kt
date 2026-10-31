package com.github.epsilon.gui.dropdown.widget

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.gui.dropdown.ListSettingPopupScreen
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.github.epsilon.settings.impl.StringListSetting

class StringListSettingWidget(setting: StringListSetting) : AbstractSetSettingWidget<StringListSetting>(setting) {

    override fun elementCount(): Int {
        return setting.size()
    }

    override fun labelText(): String {
        return EpsilonTranslations.Gui.LIST_ENTRIES.getTranslatedName()
    }

    override fun openPopup() {
        val screen = mc.gui.screen()
        if (screen is ListSettingPopupScreen) {
            screen.openStringListSettingPopup(setting)
        }
    }

}
