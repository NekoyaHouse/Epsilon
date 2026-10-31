package com.github.epsilon.gui.panel.adapter

import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.panel.component.setting.*
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.impl.*

object SettingViewFactory {

    fun create(setting: Setting<*>?): SettingRow<*>? {
        return when (setting) {
            is KeybindSetting -> KeybindSettingRow(setting)
            is BoolSetting -> BoolSettingRow(setting)
            is EnumSetting<*> -> EnumSettingRow(setting)
            is IntSetting -> IntSettingRow(setting)
            is DoubleSetting -> DoubleSettingRow(setting)
            is ColorSetting -> ColorSettingRow(setting)
            is RegistryListSetting<*> -> RegistryListSettingRow(setting)
            is StringSetting -> StringSettingRow(setting)
            is ButtonSetting -> ButtonSettingRow(setting)
            is StringListSetting -> StringListSettingRow(setting)
            else -> null
        }
    }

}
