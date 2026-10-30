package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting
import java.awt.Color

class ColorSetting(
    name: String,
    defaultValue: Color,
    private val allowAlpha: Boolean,
    dependency: Setting.Dependency?,
) : Setting<Color>(name, dependency, null) {

    init {
        initializeValue(defaultValue)
    }

    fun isAllowAlpha(): Boolean = allowAlpha

}
