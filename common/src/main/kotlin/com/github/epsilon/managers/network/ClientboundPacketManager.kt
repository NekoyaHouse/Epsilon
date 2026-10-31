package com.github.epsilon.managers.network

import com.github.epsilon.Constants
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.LevelUpdateEvent
import com.github.epsilon.managers.NotificationManager
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.*
import java.util.concurrent.LinkedBlockingQueue

object ClientboundPacketManager {

    private val packets: LinkedBlockingQueue<Packet<*>> = LinkedBlockingQueue()

    private var tracking = false
    private var shouldFlush = false

    init {
        handler<LevelUpdateEvent>(alwaysListening = true) {
            shouldFlush = true
            tracking = false
        }
    }

    fun flush() {
        while (packets.isNotEmpty()) {
            try {
                dispatch(packets.poll())
            } catch (e: Exception) {
                NotificationManager.error(
                    "Clientbound Packet",
                    EpsilonTranslations.Notifications.CLIENTBOUND_PACKET_FLUSH_FAILED.getTranslatedName() + ": " + e.message
                )
            }
        }
    }

    fun isDisallowedPacket(packet: Packet<*>): Boolean {
        return packet !is ClientboundSystemChatPacket &&
                packet !is ClientboundPlayerChatPacket &&
                packet !is ClientboundSetDisplayObjectivePacket &&
                packet !is ClientboundSetEquipmentPacket &&
                packet !is ClientboundClearTitlesPacket &&
                packet !is ClientboundSetTitleTextPacket &&
                packet !is ClientboundSetSubtitleTextPacket &&
                packet !is ClientboundSetActionBarTextPacket &&
                packet !is ClientboundBossEventPacket &&
                packet !is ClientboundAddEntityPacket &&
                packet !is ClientboundRemoveEntitiesPacket &&
                packet !is ClientboundDamageEventPacket &&
                packet !is ClientboundSoundPacket &&
                packet !is ClientboundSoundEntityPacket &&
                packet !is ClientboundSetEntityDataPacket &&
                packet !is ClientboundSetHealthPacket &&
                packet !is ClientboundContainerSetContentPacket &&
                packet !is ClientboundContainerSetSlotPacket &&
                packet !is ClientboundSetObjectivePacket &&
                packet !is ClientboundResetScorePacket &&
                packet !is ClientboundSetScorePacket
    }

    @Suppress("UNCHECKED_CAST")
    private fun dispatch(packet: Packet<*>?) {
        if (packet == null) return
        val connection = Constants.client.connection ?: return
        (packet as Packet<ClientGamePacketListener>).handle(connection)
    }

    fun startTracking() {
        tracking = true
    }

    fun stopTracking() {
        tracking = false
    }

    fun onPacketReceive(packet: Packet<*>): Boolean {
        if (Constants.client.player == null || Constants.client.level == null) return false

        if (shouldFlush) {
            flush()
            shouldFlush = false
            return false
        }

        if (!isDisallowedPacket(packet) && tracking) {
            packets.add(packet)
            return true
        }
        return false
    }

}
