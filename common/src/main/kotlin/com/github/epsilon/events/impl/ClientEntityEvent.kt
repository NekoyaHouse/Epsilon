package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity

object ClientEntityEvent {
    class TickPre(val level: ClientLevel) : IEvent, IPosting by Companion {
        companion object : EventBus()
    }

    class Added(val level: ClientLevel, val entity: Entity) : IEvent, IPosting by Companion {
        companion object : EventBus()
    }

    class Removed(val level: ClientLevel, val entityId: Int) : IEvent, IPosting by Companion {
        companion object : EventBus()
    }
}
