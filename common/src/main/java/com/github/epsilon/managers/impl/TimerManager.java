package com.github.epsilon.managers.impl;

import com.github.epsilon.modules.impl.player.Timer;

public class TimerManager {

    public float timer = 1f;
    public float lastTimer;

    public void set(float factor) {
        if (factor < 0.1f) factor = 0.1f;
        timer = factor;
    }

    public void reset() {
        timer = getDefault();
        lastTimer = timer;
    }

    public void tryReset() {
        if (lastTimer != getDefault()) {
            reset();
        }
    }

    public float get() {
        return timer;
    }

    public float getDefault() {
        Timer timer = Timer.INSTANCE;
        return timer.isEnabled() ? timer.multiplier.getValue().floatValue() : 1f;
    }

}
