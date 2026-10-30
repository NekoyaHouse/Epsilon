package com.github.epsilon.gui.panel.adapter

import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.panel.component.setting.BoolSettingRow
import com.github.epsilon.gui.panel.component.setting.ButtonSettingRow
import com.github.epsilon.gui.panel.component.setting.ColorSettingRow
import com.github.epsilon.gui.panel.component.setting.DoubleSettingRow
import com.github.epsilon.gui.panel.component.setting.EnumSettingRow
import com.github.epsilon.gui.panel.component.setting.IntSettingRow
import com.github.epsilon.gui.panel.component.setting.KeybindSettingRow
import com.github.epsilon.gui.panel.component.setting.RegistryListSettingRow
import com.github.epsilon.gui.panel.component.setting.StringListSettingRow
import com.github.epsilon.gui.panel.component.setting.StringSettingRow
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.ButtonSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.impl.IntSetting
import com.github.epsilon.settings.impl.KeybindSetting
import com.github.epsilon.settings.impl.RegistryListSetting
import com.github.epsilon.settings.impl.StringListSetting
import com.github.epsilon.settings.impl.StringSetting

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
