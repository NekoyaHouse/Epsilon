package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting

class RotationAnimationEvent(
    var yaw: Float,
    var lastYaw: Float,
    var pitch: Float,
    var lastPitch: Float,
) : IEvent<RotationAnimationEvent>, IPosting by Companion {
    companion object : EventBus()
}
