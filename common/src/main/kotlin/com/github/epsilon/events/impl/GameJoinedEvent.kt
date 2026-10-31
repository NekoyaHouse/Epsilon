package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting

class GameJoinedEvent : IEvent<GameJoinedEvent>, IPosting by Companion {
    companion object : EventBus()
}
