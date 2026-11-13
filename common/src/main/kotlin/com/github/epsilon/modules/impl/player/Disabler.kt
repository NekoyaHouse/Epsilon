package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.kernel.network.NetworkUtils
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.ViaVersionComparison
import com.github.epsilon.settings.viaOnly
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.world.entity.player.Player

object Disabler : Module("Disabler", Category.PLAYER) {

    private val multiActionsC by boolSetting("MultiActionsC", true).viaOnly(
        ViaVersionComparison.LESS_OR_EQUAL, "1.20.6"
    )
    private val aimModulo360 by boolSetting("AimModulo360", true).viaOnly(
        ViaVersionComparison.LESS_OR_EQUAL, "1.20.6"
    )

    init {
        safeEventHandler<PacketEvent.Send> { event ->
            val packet = event.packet
            if (aimModulo360 && packet is ServerboundMovePlayerPacket && packet.hasRotation()) {
                val yaw = packet.yRot
                if (yaw < 360.0f && yaw > -360.0f) {
                    packet.yRot = yaw + 720.0f
                }
            }
        }
    }

    fun handleClickSlot(oPlayer: Player, action: Runnable) {
        val handled = runSafe {
            if (!isEnabled || !multiActionsC || oPlayer !== player || !player.wasSprinting) {
                action.run()
                return@runSafe true
            }

            NetworkUtils.sendPacketNoEvent(
                ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING)
            )
            player.wasSprinting = false

            try {
                action.run()
            } finally {
                if (player.isSprinting && !player.wasSprinting) {
                    NetworkUtils.sendPacketNoEvent(
                        ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING)
                    )
                    player.wasSprinting = true
                }
            }
            true
        } ?: false
        if (!handled) action.run()
    }

}
