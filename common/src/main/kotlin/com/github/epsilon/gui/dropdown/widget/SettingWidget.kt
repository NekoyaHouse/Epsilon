package com.github.epsilon.gui.dropdown.widget

import com.github.epsilon.gui.dropdown.component.Component
import com.github.epsilon.settings.Setting

abstract class SettingWidget<S : Setting<*>>(val setting: S) : Component() {

    fun isVisible(): Boolean {
        return setting.isVisible
    }

}
