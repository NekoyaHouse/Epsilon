package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting

open class ClientTickEvent {
    class Pre : ClientTickEvent(), IEvent, IPosting by Companion {
        companion object : EventBus()
    }

    class Post : ClientTickEvent(), IEvent, IPosting by Companion {
        companion object : EventBus()
    }
}
