package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.settings.impl.DoubleSetting;
import net.minecraft.util.Mth;

import java.text.DecimalFormat;

public class DoubleSliderWidget extends AbstractSliderWidget<DoubleSetting, Double> {

    private static final DecimalFormat FORMAT = new DecimalFormat("#0.00");

    public DoubleSliderWidget(DoubleSetting setting) {
        super(setting, 16, value -> value.matches("[0-9.\\-]"));
    }

    @Override
    protected float getRatio() {
        return (float) ((getVisibleValue() - setting.getMin()) / (setting.getMax() - setting.getMin()));
    }

    @Override
    protected void updateValueFromRatio(float rawRatio) {
        double range = setting.getMax() - setting.getMin();
        double step = setting.getStep();
        double value = setting.getMin() + Math.round(rawRatio * range / step) * step;
        previewValue(Mth.clamp(value, setting.getMin(), setting.getMax()));
    }

    @Override
    protected String formatValue(Double value) {
        return FORMAT.format(value);
    }

    @Override
    protected String formatPlainValue() {
        return FORMAT.format(getVisibleValue());
    }

    @Override
    protected void commitInput() {
        String text = inputField.getText();
        if (text == null || text.isBlank() || "-".equals(text) || ".".equals(text)) {
            commitPendingValue();
            inputField.setText(formatPlainValue());
            return;
        }
        try {
            double value = Double.parseDouble(text);
            applyValueNow(value);
        } catch (NumberFormatException ignored) {
        }
        inputField.setText(formatPlainValue());
        inputField.setCursorToEnd();
    }

    @Override
    protected void syncInputValue() {
        String text = inputField.getText();
        if (text == null || text.isBlank() || "-".equals(text) || ".".equals(text)) return;
        try {
            double value = Double.parseDouble(text);
            previewValue(value);
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    protected void applyValue(Double value) {
        setting.setUnboundedValue(value);
    }

    @Override
    protected Double getMin() {
        return setting.getMin();
    }

    @Override
    protected Double getMax() {
        return setting.getMax();
    }

}
