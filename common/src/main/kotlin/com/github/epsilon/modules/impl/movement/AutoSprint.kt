package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly
import com.github.epsilon.modules.impl.player.InvManager

object AutoSprint : Module("Auto Sprint", Category.MOVEMENT) {

    init {
        nonNullListener<ClientTickEvent.Pre>(EventPriority.LOWEST) {
            // ElytraFly 启动时会停一次疾跑，这段时间内不要立刻把疾跑键按回去。
            if (ElytraFly.isSprintSuppressed) return@nonNullListener

            val sprintTransition = InvManager.isEnabled && InvManager.isSprintTransitionPending
            if (sprintTransition) return@nonNullListener

            mc.options.keySprint.isDown = mc.gui.screen() == null
        }
    }

    override fun onDisable() {
        if (mc.options.keySprint.isDown) {
            mc.options.keySprint.isDown = false
        }
    }

}
