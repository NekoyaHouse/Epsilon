package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting
import net.minecraft.util.Mth

class DoubleSetting(
    name: String,
    defaultValue: Double,
    val min: Double,
    val max: Double,
    val step: Double,
    dependency: Dependency?,
    onChanged: ((Double) -> Unit)?,
) : Setting<Double>(name, dependency, onChanged) {

    init {
        initializeValue(defaultValue)
    }

    override var value: Double
        get() = super.value
        set(newValue) {
            writeValue(Mth.clamp(newValue, min, max))
        }

    fun setUnboundedValue(newValue: Double) {
        writeValue(newValue)
    }

}
