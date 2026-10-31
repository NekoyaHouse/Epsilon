package com.github.epsilon.utils.rotation

class Rotation(var yaw: Float, var pitch: Float) {

    fun set(yaw: Float, pitch: Float) {
        this.yaw = yaw
        this.pitch = pitch
    }

    fun set(vector2f: Rotation) {
        this.yaw = vector2f.yaw
        this.pitch = vector2f.pitch
    }

}
