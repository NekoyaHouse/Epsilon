package com.github.epsilon.managers.network

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.events.api.AlwaysListening
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.LevelUpdateEvent
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.github.epsilon.managers.NotificationManager
import net.minecraft.network.protocol.Packet
import java.util.concurrent.LinkedBlockingQueue

// This is BlinkManager
object ServerboundPacketManager : AlwaysListening {

    val packets: LinkedBlockingQueue<Packet<*>> = LinkedBlockingQueue()

    var blinking = false
    var forceFlush = false

    init {
        handler<LevelUpdateEvent>(alwaysListening = true) {
            forceFlush = true
            blinking = false
        }
    }

    fun flush() {
        while (packets.isNotEmpty()) {
            try {
                mc.connection!!.send(packets.poll())
            } catch (e: Exception) {
                NotificationManager.error(
                    "Serverbound Packet",
                    EpsilonTranslations.Notifications.SERVERBOUND_PACKET_FLUSH_FAILED.getTranslatedName() + ": " + e.message
                )
            }
        }
    }

    fun stopBlinking() {
        blinking = false
    }

    fun startBlinking() {
        blinking = true
    }

    fun onPacketSend(packet: Packet<*>): Boolean {
        if (forceFlush) {
            flush()
            forceFlush = false
            return false
        }

        if (!blinking) return false

        if (mc.player == null || mc.level == null) return false

        packets.add(packet)
        return true
    }

}
