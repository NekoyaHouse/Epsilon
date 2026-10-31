package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting

class UseItemEvent(initialYaw: Float, initialPitch: Float) : IEvent<UseItemEvent>, IPosting by Companion {
    companion object : EventBus()

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
