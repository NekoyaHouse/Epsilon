package com.github.epsilon.modules.impl.player

import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.ViaVersionComparison
import com.github.epsilon.settings.viaOnly
import com.github.epsilon.utils.network.NetworkUtils
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket

object Disabler : Module("Disabler", Category.PLAYER) {

    private val multiActionsC by boolSetting("MultiActionsC", true).viaOnly(
        ViaVersionComparison.LESS_OR_EQUAL, "1.20.6"
    )

    fun withSprintStopped(action: Runnable) {
        if (!isEnabled || !multiActionsC) return
        runSafe {
            if (player.isSprinting) {
                NetworkUtils.sendPacketNoEvent(
                    ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING)
                )

                action.run()

                NetworkUtils.sendPacketNoEvent(
                    ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING)
                )
            } else action.run()
        }
    }

}
