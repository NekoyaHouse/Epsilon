package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting

class StringSetting(
    name: String,
    defaultValue: String,
    dependency: Dependency?,
    onChanged: ((String) -> Unit)?,
) : Setting<String>(name, dependency, onChanged) {

    constructor(name: String, defaultValue: String, dependency: Dependency?) :
            this(name, defaultValue, dependency, null)

    init {
        initializeValue(defaultValue)
    }

}
