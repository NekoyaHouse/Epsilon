package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting

class BoolSetting(
    name: String,
    defaultValue: Boolean,
    visible: Visibility?,
    onChanged: ((Boolean) -> Unit)?,
) : Setting<Boolean>(name, visible, onChanged) {

    /** 保存用户选择；临时版本限制不能覆盖配置。 */
    val configuredValue: Boolean get() = super.value

    override var value: Boolean
        get() = super.value && isViaSupported()
        set(newValue) {
            writeValueSilently(newValue)
            onChanged?.invoke(value)
        }

    init {
        initializeValue(defaultValue)
    }

}
