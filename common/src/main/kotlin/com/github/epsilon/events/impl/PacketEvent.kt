package com.github.epsilon.events.impl

import com.github.epsilon.events.api.Cancellable
import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting
import net.minecraft.network.protocol.Packet

open class PacketEvent {

    class Send(var packet: Packet<*>) : Cancellable(), IEvent, IPosting by Companion {
        companion object : EventBus()
    }

    class ReceiveNetty(var packet: Packet<*>) : Cancellable(), IEvent, IPosting by Companion {
        companion object : EventBus()
    }

    class ReceiveMain(val packet: Packet<*>) : IEvent, IPosting by Companion {
        companion object : EventBus()
    }

}
