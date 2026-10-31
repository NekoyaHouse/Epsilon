package com.github.epsilon.gui.dropdown.widget

import com.github.epsilon.settings.impl.IntSetting
import net.minecraft.util.Mth
import java.util.function.Predicate
import kotlin.math.roundToInt

class IntSliderWidget(setting: IntSetting) :
    AbstractSliderWidget<IntSetting, Int>(setting, 12, Predicate { value -> value.matches(Regex("[0-9-]")) }) {

    override fun getRatio(): Float {
        return (getVisibleValue() - setting.range.first).toFloat() / (setting.range.last - setting.range.first).toFloat()
    }

    override fun updateValueFromRatio(rawRatio: Float) {
        val range = setting.range.last - setting.range.first
        val step = setting.step
        val value = setting.range.first + (rawRatio * range / step).roundToInt() * step
        previewValue(Mth.clamp(value, setting.range.first, setting.range.last))
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
        return setting.range.first
    }

    override fun getMax(): Int {
        return setting.range.last
    }

}
