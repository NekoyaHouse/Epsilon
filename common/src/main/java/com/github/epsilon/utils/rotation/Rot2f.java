package com.github.epsilon.utils.rotation;

public class Rot2f {

    private float yaw;
    private float pitch;

    /**
     * 创建偏航角和俯仰角组成的旋转值。
     *
     * @param yaw   偏航角
     * @param pitch 俯仰角
     */
    public Rot2f(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * 获取偏航角。
     *
     * @return 获取或计算得到的结果
     */
    public float getYaw() {
        return yaw;
    }

    /**
     * 获取俯仰角。
     *
     * @return 获取或计算得到的结果
     */
    public float getPitch() {
        return pitch;
    }

    /**
     * 设置偏航角。
     *
     * @param yaw 偏航角
     */
    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    /**
     * 设置俯仰角。
     *
     * @param pitch 俯仰角
     */
    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    /**
     * 同时更新该旋转值。
     *
     * @param yaw   偏航角
     * @param pitch 俯仰角
     */
    public void set(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * 同时更新该旋转值。
     *
     * @param vector2f 作为新值来源的旋转对象
     */
    public void set(Rot2f vector2f) {
        this.yaw = vector2f.yaw;
        this.pitch = vector2f.pitch;
    }

}
