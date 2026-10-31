package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.network.NetworkUtils
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket

object Disabler : Module("Disabler", Category.PLAYER) {

    private val duplicateSlot by boolSetting("ACA Duplicate Slot", true)
    private val fastSwitch by boolSetting("ACA Fast Switch", true)

    private var lastSlot = -1

    init {
        nonNullHandler<PacketEvent.Send> { event ->
            val packet = event.packet
            if (!duplicateSlot || packet !is ServerboundSetCarriedItemPacket) return@nonNullHandler

            val nextSlot = packet.slot
            if (nextSlot < 0 || nextSlot > 8 || nextSlot == lastSlot && !fastSwitch) {
                event.cancel()
                return@nonNullHandler
            }
            if (fastSwitch && lastSlot >= 0 && nextSlot != lastSlot && !((lastSlot == 0 && nextSlot == 8) || (lastSlot == 8 && nextSlot == 0))) {
                event.cancel()
                val step = if (lastSlot < nextSlot) 1 else -1
                var slot = lastSlot + step
                while (slot != nextSlot) {
                    NetworkUtils.sendPacketNoEvent(ServerboundSetCarriedItemPacket(slot))
                    slot += step
                }
                NetworkUtils.sendPacketNoEvent(packet)
            }
            lastSlot = nextSlot
        }
    }

}
