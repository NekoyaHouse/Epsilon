package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.settings.impl.IntSetting;
import net.minecraft.util.Mth;

public class IntSliderWidget extends AbstractSliderWidget<IntSetting, Integer> {

    public IntSliderWidget(IntSetting setting) {
        super(setting, 12, value -> value.matches("[0-9-]"));
    }

    @Override
    protected float getRatio() {
        return (float) (getVisibleValue() - setting.getMin()) / (float) (setting.getMax() - setting.getMin());
    }

    @Override
    protected void updateValueFromRatio(float rawRatio) {
        int range = setting.getMax() - setting.getMin();
        int step = setting.getStep();
        int value = setting.getMin() + Math.round(rawRatio * range / step) * step;
        previewValue(Mth.clamp(value, setting.getMin(), setting.getMax()));
    }

    @Override
    protected String formatValue(Integer value) {
        return Integer.toString(value);
    }

    @Override
    protected String formatPlainValue() {
        return Integer.toString(getVisibleValue());
    }

    @Override
    protected void commitInput() {
        String text = inputField.getText();
        if (text == null || text.isBlank() || "-".equals(text)) {
            commitPendingValue();
            inputField.setText(formatPlainValue());
            return;
        }
        try {
            int value = Integer.parseInt(text);
            applyValueNow(value);
        } catch (NumberFormatException ignored) {
        }
        inputField.setText(formatPlainValue());
        inputField.setCursorToEnd();
    }

    @Override
    protected void syncInputValue() {
        String text = inputField.getText();
        if (text == null || text.isBlank() || "-".equals(text)) return;
        try {
            int value = Integer.parseInt(text);
            previewValue(value);
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    protected void applyValue(Integer value) {
        setting.setUnboundedValue(value);
    }

    @Override
    protected Integer getMin() {
        return setting.getMin();
    }

    @Override
    protected Integer getMax() {
        return setting.getMax();
    }

}
