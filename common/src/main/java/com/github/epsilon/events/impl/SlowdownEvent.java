package com.github.epsilon.events.impl;

public class SlowdownEvent {

    private boolean slowdown;

    public SlowdownEvent(boolean slowdown) {
        this.slowdown = slowdown;
    }

    public boolean isSlowdown() {
        return this.slowdown;
    }

    public void setSlowdown(boolean slowdown) {
        this.slowdown = slowdown;
    }

}
