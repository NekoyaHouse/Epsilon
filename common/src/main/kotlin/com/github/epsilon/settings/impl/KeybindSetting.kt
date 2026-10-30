package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting

class KeybindSetting(
    name: String,
    defaultValue: Int,
    dependency: Setting.Dependency?,
) : Setting<Int>(name, dependency, null) {

    init {
        initializeValue(defaultValue)
    }

}
