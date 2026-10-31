package com.github.epsilon.events.impl

import com.github.epsilon.events.bus.Cancellable
import net.minecraft.network.protocol.Packet

open class PacketEvent {

    class Send(var packet: Packet<*>) : Cancellable()

    class Receive(var packet: Packet<*>) : Cancellable()

}
