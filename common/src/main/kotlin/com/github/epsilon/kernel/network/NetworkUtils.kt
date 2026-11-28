package com.github.epsilon.kernel.network

import com.github.epsilon.kernel.MinecraftWrapper.mc
import net.minecraft.network.protocol.Packet

object NetworkUtils {

    val bypassedPackets: MutableSet<Packet<*>> = HashSet()

    fun sendPacketNoEvent(packet: Packet<*>) {
        val connection = mc.connection ?: return
        bypassedPackets.add(packet)
        connection.send(packet)
    }

}
