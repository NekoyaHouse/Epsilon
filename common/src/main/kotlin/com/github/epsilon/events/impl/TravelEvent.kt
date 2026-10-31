package com.github.epsilon.events.impl

import com.github.epsilon.events.api.Cancellable
import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting

class TravelEvent : Cancellable(), IEvent<TravelEvent>, IPosting by Companion {
    companion object : EventBus()
}
