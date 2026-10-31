package com.github.epsilon.gui.dropdown.widget

import com.github.epsilon.settings.impl.IntSetting
import kotlin.math.roundToInt
import net.minecraft.util.Mth
import java.util.function.Predicate

class IntSliderWidget(setting: IntSetting) :
    AbstractSliderWidget<IntSetting, Int>(setting, 12, Predicate { value -> value.matches(Regex("[0-9-]")) }) {

    override fun getRatio(): Float {
        return (getVisibleValue() - setting.min).toFloat() / (setting.max - setting.min).toFloat()
    }

    override fun updateValueFromRatio(rawRatio: Float) {
        val range = setting.max - setting.min
        val step = setting.step
        val value = setting.min + (rawRatio * range / step).roundToInt() * step
        previewValue(Mth.clamp(value, setting.min, setting.max))
    }

    override fun formatValue(value: Int): String {
        return value.toString()
    }

    override fun formatPlainValue(): String {
        return getVisibleValue().toString()
    }

    override fun commitInput() {
        val text = inputField.text
        if (text.isBlank() || "-" == text) {
            commitPendingValue()
            inputField.text = formatPlainValue()
            return
        }
        try {
            val value = text.toInt()
            applyValueNow(value)
        } catch (ignored: NumberFormatException) {
        }
        inputField.text = formatPlainValue()
        inputField.setCursorToEnd()
    }

    override fun syncInputValue() {
        val text = inputField.text
        if (text.isBlank() || "-" == text) return
        try {
            val value = text.toInt()
            previewValue(value)
        } catch (ignored: NumberFormatException) {
        }
    }

    override fun applyValue(value: Int) {
        setting.setUnboundedValue(value)
    }

    override fun getMin(): Int {
        return setting.min
    }

    override fun getMax(): Int {
        return setting.max
    }

}
