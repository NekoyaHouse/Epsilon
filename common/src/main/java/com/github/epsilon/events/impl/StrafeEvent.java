package com.github.epsilon.events.impl;

public class StrafeEvent {

    private float yaw;

    public StrafeEvent(float yaw) {
        this.yaw = yaw;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

}
