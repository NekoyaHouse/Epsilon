package com.github.epsilon.utils.rotation;

public class Rot2f {

    private float yaw;
    private float pitch;

    public Rot2f(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void set(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public void set(Rot2f vector2f) {
        this.yaw = vector2f.yaw;
        this.pitch = vector2f.pitch;
    }

}
