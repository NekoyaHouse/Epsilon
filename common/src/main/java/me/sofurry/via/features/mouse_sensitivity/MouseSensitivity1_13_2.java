package me.sofurry.via.features.mouse_sensitivity;

import it.unimi.dsi.fastutil.floats.FloatIntPair;

public class MouseSensitivity1_13_2 {

    public static FloatIntPair get1_13SliderValue(final float value1_14) {
        final int oldSliderWidth = 150 - 8;
        final int mousePos = (int) (oldSliderWidth * value1_14);
        final float oldValue = mousePos / (float) oldSliderWidth;
        final int oldDisplay = (int) (oldValue * 200);
        return FloatIntPair.of(oldValue, oldDisplay);
    }

}
