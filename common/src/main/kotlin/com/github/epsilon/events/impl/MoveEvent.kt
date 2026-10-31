package com.github.epsilon.events.impl

import com.github.epsilon.events.api.Cancellable
import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting

class MoveEvent(var x: Double, var y: Double, var z: Double) : Cancellable(), IEvent<MoveEvent>, IPosting by Companion {
    companion object : EventBus()
}
