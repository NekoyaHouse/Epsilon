package com.github.epsilon.events.impl;

public class UseItemEvent {

    private float yaw;
    private float pitch;
    private boolean modified;

    public UseItemEvent(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.modified = false;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isModified() {
        return modified;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
        modified = true;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
        modified = true;
    }

}
