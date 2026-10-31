package com.github.epsilon.events.impl

class UseItemEvent(initialYaw: Float, initialPitch: Float) {

    var yaw: Float = initialYaw
        set(value) {
            field = value
            isModified = true
        }

    var pitch: Float = initialPitch
        set(value) {
            field = value
            isModified = true
        }

    var isModified: Boolean = false
        private set

}
