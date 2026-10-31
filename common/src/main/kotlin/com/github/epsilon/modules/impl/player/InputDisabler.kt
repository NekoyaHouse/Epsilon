package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.network.NetworkUtils
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.entity.player.Input

object InputDisabler : Module("Input Disabler", Category.PLAYER) {

    private var oldInput: Input? = null
    private var shouldRestore = false

    init {
        nonNullHandler<PacketEvent.Send> { event ->
            val packet = event.packet
            if (packet !is ServerboundContainerClickPacket && packet !is ServerboundContainerClosePacket) return@nonNullHandler

            event.cancel()
            spoofInput(player, connection)
            NetworkUtils.sendPacketNoEvent(packet)
            restoreInput(player)
        }
    }

    private fun spoofInput(player: LocalPlayer, netHandler: ClientPacketListener) {
        if (shouldRestore) return
        oldInput = player.input.keyPresses
        player.input.keyPresses = Input.EMPTY
        netHandler.send(ServerboundPlayerInputPacket(Input.EMPTY))
        player.lastSentInput = Input.EMPTY
        shouldRestore = true
    }

    private fun restoreInput(player: LocalPlayer) {
        if (!shouldRestore) return
        player.input.keyPresses = oldInput!!
        oldInput = null
        shouldRestore = false
    }

}
