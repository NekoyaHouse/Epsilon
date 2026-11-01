package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.network.NetworkUtils
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
        onEnable {
            ticks = 20
            stage = 0
            packets.clear()
        }

        onDisable {
            packets.clear()
        }

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

        safeEventHandler<PlayerTickEvent.Pre> { event ->
            if (player.onGround()) {
                isEnabled = false
                return@safeEventHandler
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

}
