package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.movement.Velocity
import net.minecraft.client.player.LocalPlayer

object Criticals : Module("Criticals", Category.COMBAT) {

    var fallTicks = 0

    private var stopSprinting = false

    init {
        safeEventHandler<ClientTickEvent.Pre>(EventPriority.HIGHEST) {
            if (!canCrit(player) || player.fallDistance >= 1.0) {
                fallTicks = 0
            } else {
                fallTicks++
            }
        }

        safeEventHandler<ClientTickEvent.Pre>(EventPriority.LOWEST) {
            stopSprinting = fallTicks in 1..<3 &&
                    player.isSprinting &&
                    KillAura.target != null &&
                    Velocity.attackQueue == 0
        }

        safeEventHandler<KeyboardInputEvent>(EventPriority.LOWEST) { event ->
            if (!stopSprinting) return@safeEventHandler
            stopSprinting = false

            if (fallTicks in 1..<3 &&
                canCrit(player) &&
                player.isSprinting &&
                KillAura.target != null &&
                Velocity.attackQueue == 0 &&
                event.forward > 0.0f
            ) {
                event.apply { sprint = false }
                player.isSprinting = false
                mc.options.keySprint.isDown = false
            }
        }
    }

    override fun onDisable() {
        fallTicks = 0
        stopSprinting = false
    }

    private fun canCrit(player: LocalPlayer): Boolean {
        return player.fallDistance > 0.0 && !player.onGround() && !player.onClimbable() &&
                !player.isInWater && !player.isMobilityRestricted && !player.isPassenger
    }

}
