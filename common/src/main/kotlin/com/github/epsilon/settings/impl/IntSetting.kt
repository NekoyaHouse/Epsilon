package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting

class IntSetting(
    name: String,
    defaultValue: Int,
    val range: IntRange,
    val step: Int,
    visible: Visibility?,
    onChanged: ((Int) -> Unit)?,
) : Setting<Int>(name, visible, onChanged) {

    init {
        initializeValue(defaultValue)
    }

    override var value: Int
        get() = super.value
        set(newValue) {
            writeValue(newValue.coerceIn(range))
        }

    fun setUnboundedValue(newValue: Int) {
        writeValue(newValue)
    }

}
