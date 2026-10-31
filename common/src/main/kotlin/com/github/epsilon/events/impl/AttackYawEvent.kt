package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting

class AttackYawEvent(var yaw: Float) : IEvent<AttackYawEvent>, IPosting by Companion {
    companion object : EventBus()
}
