package com.github.epsilon.utils.network

import com.github.epsilon.Constants
import net.minecraft.network.protocol.Packet
import java.util.HashSet

object NetworkUtils {

    val bypassedPackets: MutableSet<Packet<*>> = HashSet()

    /**
     * 发送网络包，并使该包绕过 Epsilon 的发送事件。
     *
     * @param packet 待发送或过滤的网络包
     */
    fun sendPacketNoEvent(packet: Packet<*>) {
        val connection = Constants.mc.getConnection() ?: return
        bypassedPackets.add(packet)
        connection.send(packet)
    }

}
