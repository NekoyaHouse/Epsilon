package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting
import java.awt.Color

class ColorSetting(
    name: String,
    defaultValue: Color,
    private val allowAlpha: Boolean,
    visible: Visibility?,
) : Setting<Color>(name, defaultValue, visible, null) {

    fun isAllowAlpha(): Boolean = allowAlpha

}
