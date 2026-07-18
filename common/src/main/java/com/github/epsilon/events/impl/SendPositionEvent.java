package com.github.epsilon.events.impl;

import com.github.epsilon.events.bus.Cancellable;

public class SendPositionEvent extends Cancellable {

    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private boolean onGround;

    public SendPositionEvent(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

}
