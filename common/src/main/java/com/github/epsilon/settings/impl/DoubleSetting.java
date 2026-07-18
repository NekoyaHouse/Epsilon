package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

public class DoubleSetting extends Setting<Double> {

    private final double min;
    private final double max;
    private final double step;

    public DoubleSetting(String name, double defaultValue, double min, double max, double step, Dependency dependency, Consumer<Double> onChanged) {
        super(name, dependency, onChanged);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    @Override
    public void setValue(Double value) {
        super.setValue(Mth.clamp(value, min, max));
    }

    public void setUnboundedValue(Double value) {
        super.setValue(value);
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

}
