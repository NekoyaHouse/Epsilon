package com.github.epsilon.events.impl

import com.github.epsilon.events.api.Cancellable
import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

class AttackEntityEvent(val player: Player, val entity: Entity) : Cancellable(), IEvent<AttackEntityEvent>,
    IPosting by Companion {
    companion object : EventBus()
}
