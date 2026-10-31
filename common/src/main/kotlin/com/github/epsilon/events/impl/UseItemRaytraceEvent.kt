package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting

class UseItemRaytraceEvent(var yaw: Float, var pitch: Float) : IEvent<UseItemRaytraceEvent>, IPosting by Companion {
    companion object : EventBus()
}
