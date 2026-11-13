package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting

class JumpEvent(var yaw: Float) : IEvent, IPosting by Companion {
    companion object : EventBus()
}
