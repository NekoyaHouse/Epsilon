package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting

class StrafeEvent(var yaw: Float) : IEvent<StrafeEvent>, IPosting by Companion {
    companion object : EventBus()
}
