package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly
import com.github.epsilon.modules.impl.player.InvManager

object AutoSprint : Module("Auto Sprint", Category.MOVEMENT) {

    init {
        onDisable {
            if (mc.options.keySprint.isDown) {
                mc.options.keySprint.isDown = false
            }
        }

        safeEventHandler<ClientTickEvent.Post> {
            val guiCheck = mc.gui.screen() == null
            val elytraFlyCheck = ElytraFly.isSprintSuppressed
            val sprintTransition = InvManager.isEnabled && InvManager.isSprintTransitionPending
            mc.options.keySprint.isDown = guiCheck && !elytraFlyCheck && !sprintTransition
        }
    }

}
