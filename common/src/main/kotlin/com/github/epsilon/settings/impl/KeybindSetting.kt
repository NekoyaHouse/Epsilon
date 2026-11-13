package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting

class KeybindSetting(
    name: String,
    defaultValue: Int,
    visible: Visibility?,
) : Setting<Int>(name, defaultValue, visible, null)
