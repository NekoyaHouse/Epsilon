package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting

class ButtonSetting(
    name: String,
    func: Runnable,
    visible: Visibility?,
) : Setting<Runnable>(name, visible, null) {

    init {
        initializeValue(func)
    }

}
