package com.github.epsilon.managers

import com.github.epsilon.events.api.AlwaysListening
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.kernel.MinecraftWrapper.mc
import net.minecraft.network.protocol.game.ClientboundSetScorePacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import java.util.concurrent.ConcurrentHashMap

object HealthManager : AlwaysListening {

    private val scoreboardHealth = ConcurrentHashMap<String, Int>()

    init {
        handler<PacketEvent.Receive>(alwaysListening = true) { event -> onPacket(event) }
    }

    fun getHealth(entity: Entity): Float {
        if (entity is LivingEntity) {
            val name = entity.name.string
            val scoreHealth = scoreboardHealth[name]
            return scoreHealth?.toFloat() ?: (entity.health + entity.absorptionAmount)
        }
        return 0f
    }

    private fun onPacket(event: PacketEvent.Receive) {
        if (mc.player == null || mc.level == null) return

        val packet = event.packet
        if (packet is ClientboundSetScorePacket) {
            val objectiveName = packet.objectiveName()
            if (isHealthObjective(objectiveName)) {
                scoreboardHealth[packet.owner()] = packet.score()
            }
        }
    }

    private fun isHealthObjective(objectiveName: String): Boolean {
        return "belowHealth".equals(objectiveName, ignoreCase = true) ||
                "health".equals(objectiveName, ignoreCase = true)
    }

}
