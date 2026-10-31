package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting

class BoolSetting(
    name: String,
    defaultValue: Boolean,
    visible: Visibility?,
    onChanged: ((Boolean) -> Unit)?,
) : Setting<Boolean>(name, visible, onChanged) {

    init {
        initializeValue(defaultValue)
    }

}
