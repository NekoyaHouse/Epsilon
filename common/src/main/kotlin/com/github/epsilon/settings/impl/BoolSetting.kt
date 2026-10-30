package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting

class BoolSetting(
    name: String,
    defaultValue: Boolean,
    dependency: Setting.Dependency?,
    onChanged: ((Boolean) -> Unit)?,
) : Setting<Boolean>(name, dependency, onChanged) {

    init {
        initializeValue(defaultValue)
    }

}
