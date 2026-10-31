package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting

class AfterRender3DEvent : IEvent<AfterRender3DEvent>, IPosting by Companion {
    companion object : EventBus()
}
