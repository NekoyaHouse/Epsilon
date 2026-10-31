package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.network.NetworkUtils
import com.github.epsilon.utils.safety.player
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object Stuck : Module("Stuck", Category.MOVEMENT) {

    private var ticks = 0
    private var stage = 0
    private val packets: Queue<Packet<*>> = ConcurrentLinkedQueue()

    init {
        handler<PacketEvent.Send> { event ->
            val packet = event.packet
            if (stage == 0) {
                if (packet is ServerboundUseItemPacket || packet is ServerboundUseItemOnPacket) {
                    packets.add(packet)
                    event.cancel()
                    stage = 1
                }
            }
        }

        nonNullHandler<PlayerTickEvent.Pre> { event ->
            if (player.onGround()) {
                isEnabled = false
                return@nonNullHandler
            }
            when (stage) {
                0 -> {
                    if (ticks > 0) {
                        event.cancel()
                        if (ticks == 10) {
                            NetworkUtils.sendPacketNoEvent(
                                ServerboundMovePlayerPacket.StatusOnly(player.onGround(), player.horizontalCollision)
                            )
                        }
                        ticks--
                    } else {
                        ticks = 20
                    }
                }
                1 -> stage = 2
                2 -> {
                    while (packets.isNotEmpty()) {
                        val poll = packets.poll() ?: break
                        NetworkUtils.sendPacketNoEvent(poll)
                    }
                    stage = 0
                }
            }
        }
    }

    override fun onEnable() {
        ticks = 20
        stage = 0
        packets.clear()
    }

    override fun onDisable() {
        packets.clear()
    }

}
