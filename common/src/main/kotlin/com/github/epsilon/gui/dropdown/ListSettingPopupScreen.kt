package com.github.epsilon.gui.dropdown

import com.github.epsilon.settings.impl.RegistryListSetting
import com.github.epsilon.settings.impl.StringListSetting

interface ListSettingPopupScreen {

    fun openRegistryListSettingPopup(setting: RegistryListSetting<*>)

    fun openStringListSettingPopup(setting: StringListSetting)

}
