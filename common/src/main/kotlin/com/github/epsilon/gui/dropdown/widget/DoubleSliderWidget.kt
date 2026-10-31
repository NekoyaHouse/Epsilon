package com.github.epsilon.gui.dropdown.widget

import com.github.epsilon.settings.impl.DoubleSetting
import net.minecraft.util.Mth
import java.text.DecimalFormat
import java.util.function.Predicate
import kotlin.math.roundToInt

class DoubleSliderWidget(setting: DoubleSetting) :
    AbstractSliderWidget<DoubleSetting, Double>(setting, 16, Predicate { value -> value.matches(Regex("[0-9.\\-]")) }) {

    override fun getRatio(): Float {
        return ((getVisibleValue() - setting.range.start) / (setting.range.endInclusive - setting.range.start)).toFloat()
    }

    override fun updateValueFromRatio(rawRatio: Float) {
        val range = setting.range.endInclusive - setting.range.start
        val step = setting.step
        val value = setting.range.start + (rawRatio * range / step).roundToInt() * step
        previewValue(Mth.clamp(value, setting.range.start, setting.range.endInclusive))
    }

    override fun formatValue(value: Double): String {
        return FORMAT.format(value)
    }

    override fun formatPlainValue(): String {
        return FORMAT.format(getVisibleValue())
    }

    override fun commitInput() {
        val text = inputField.text
        if (text.isBlank() || "-" == text || "." == text) {
            commitPendingValue()
            inputField.text = formatPlainValue()
            return
        }
        try {
            val value = text.toDouble()
            applyValueNow(value)
        } catch (ignored: NumberFormatException) {
        }
        inputField.text = formatPlainValue()
        inputField.setCursorToEnd()
    }

    override fun syncInputValue() {
        val text = inputField.text
        if (text.isBlank() || "-" == text || "." == text) return
        try {
            val value = text.toDouble()
            previewValue(value)
        } catch (ignored: NumberFormatException) {
        }
    }

    override fun applyValue(value: Double) {
        setting.setUnboundedValue(value)
    }

    override fun getMin(): Double {
        return setting.range.start
    }

    override fun getMax(): Double {
        return setting.range.endInclusive
    }

    companion object {
        private val FORMAT: DecimalFormat = DecimalFormat("#0.00")
    }

}
