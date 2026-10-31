package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting

class DoubleSetting(
    name: String,
    defaultValue: Double,
    val range: ClosedFloatingPointRange<Double>,
    val step: Double,
    visible: Visibility?,
    onChanged: ((Double) -> Unit)?,
) : Setting<Double>(name, visible, onChanged) {

    init {
        initializeValue(defaultValue)
    }

    override var value: Double
        get() = super.value
        set(newValue) {
            writeValue(newValue.coerceIn(range))
        }

    fun setUnboundedValue(newValue: Double) {
        writeValue(newValue)
    }

}
