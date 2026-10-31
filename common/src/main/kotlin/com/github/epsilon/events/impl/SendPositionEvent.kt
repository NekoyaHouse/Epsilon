package com.github.epsilon.events.impl

import com.github.epsilon.events.api.Cancellable
import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting

class SendPositionEvent(
    var x: Double,
    var y: Double,
    var z: Double,
    var yaw: Float,
    var pitch: Float,
    var isOnGround: Boolean,
) : Cancellable(), IEvent<SendPositionEvent>, IPosting by Companion {
    companion object : EventBus()
}
