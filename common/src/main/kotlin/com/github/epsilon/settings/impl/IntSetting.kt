package com.github.epsilon.settings.impl

import com.github.epsilon.settings.Setting
import net.minecraft.util.Mth

class IntSetting(
    name: String,
    defaultValue: Int,
    val min: Int,
    val max: Int,
    val step: Int,
    dependency: Setting.Dependency?,
    onChanged: ((Int) -> Unit)?,
) : Setting<Int>(name, dependency, onChanged) {

    init {
        initializeValue(defaultValue)
    }

    override var value: Int
        get() = super.value
        set(newValue) {
            writeValue(Mth.clamp(newValue, min, max))
        }

    fun setUnboundedValue(newValue: Int) {
        writeValue(newValue)
    }

}
