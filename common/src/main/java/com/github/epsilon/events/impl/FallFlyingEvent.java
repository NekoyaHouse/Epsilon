package com.github.epsilon.events.impl;

public class FallFlyingEvent {

    private float yaw;
    private float pitch;

    public FallFlyingEvent(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getPitch() {
        return this.pitch;
    }

}
