package com.github.epsilon.utils.rotation

class Rot2f(var yaw: Float, var pitch: Float) {

    /**
     * 同时更新该旋转值。
     */
    fun set(yaw: Float, pitch: Float) {
        this.yaw = yaw
        this.pitch = pitch
    }

    /**
     * 同时更新该旋转值。
     *
     * @param vector2f 作为新值来源的旋转对象
     */
    fun set(vector2f: Rot2f) {
        this.yaw = vector2f.yaw
        this.pitch = vector2f.pitch
    }

}
