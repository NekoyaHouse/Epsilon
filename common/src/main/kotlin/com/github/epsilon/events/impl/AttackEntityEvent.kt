package com.github.epsilon.events.impl

import com.github.epsilon.events.bus.Cancellable
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

class AttackEntityEvent(val player: Player, val entity: Entity) : Cancellable()
