package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting

class StringSetting(
    name: String,
    defaultValue: String,
    visible: Visibility?,
    onChanged: ((String) -> Unit)?,
) : Setting<String>(name, defaultValue, visible, onChanged) {

    constructor(name: String, defaultValue: String, visible: Visibility?) :
            this(name, defaultValue, visible, null)

}
