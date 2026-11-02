package com.github.epsilon.modules.impl.player

import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.ViaVersionComparison
import com.github.epsilon.settings.viaOnly
import com.github.epsilon.utils.network.NetworkUtils
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.world.entity.player.Player

object Disabler : Module("Disabler", Category.PLAYER) {

    private val multiActionsC by boolSetting("MultiActionsC", true).viaOnly(
        ViaVersionComparison.LESS_OR_EQUAL, "1.20.6"
    )

    fun handleClickSlot(oPlayer: Player, action: Runnable) {
        runSafe {
            if (!isEnabled || !multiActionsC || oPlayer != player || !player.isSprinting) {
                action.run()
                return
            }

            NetworkUtils.sendPacketNoEvent(
                ServerboundPlayerCommandPacket(oPlayer, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING)
            )

            action.run()

            NetworkUtils.sendPacketNoEvent(
                ServerboundPlayerCommandPacket(oPlayer, ServerboundPlayerCommandPacket.Action.START_SPRINTING)
            )
        }
    }

}
