package com.github.epsilon.events.impl

import com.github.epsilon.events.api.Cancellable
import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting

open class PlayerTickEvent {

    class Pre : Cancellable(), IEvent<Pre>, IPosting by Companion {
        companion object : EventBus()
    }

    class Post : IEvent<Post>, IPosting by Companion {
        companion object : EventBus()
    }

}
