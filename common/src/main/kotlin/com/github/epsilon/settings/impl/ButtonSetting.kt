package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting

class ButtonSetting(
    name: String,
    func: Runnable,
    dependency: Setting.Dependency?,
) : Setting<Runnable>(name, dependency, null) {

    init {
        initializeValue(func)
    }

}
