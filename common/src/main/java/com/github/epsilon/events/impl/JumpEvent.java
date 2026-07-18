package com.github.epsilon.events.impl;

public class JumpEvent {

    private float yaw;

    public JumpEvent(float yaw) {
        this.yaw = yaw;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

}
