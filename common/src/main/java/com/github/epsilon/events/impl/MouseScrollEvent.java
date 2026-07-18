package com.github.epsilon.events.impl;

import com.github.epsilon.events.bus.Cancellable;

public class MouseScrollEvent extends Cancellable {

    private final double value;

    public MouseScrollEvent(double value) {
        this.value = value;
    }

    public double getValue() {
        return value;
    }

}
