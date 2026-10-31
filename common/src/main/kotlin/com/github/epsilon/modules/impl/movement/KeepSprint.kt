package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.AfterSendPositionEvent
import com.github.epsilon.events.impl.AttackSlowdownEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.safety.player
import com.github.epsilon.utils.safety.runSafe

object KeepSprint : Module("Keep Sprint", Category.MOVEMENT) {

    private enum class Mode {
        Vanilla,
        Legit,
    }

    private val mode by enumSetting("Mode", Mode.Vanilla)

    private var shouldReSprint = false

    init {
        handler<AttackSlowdownEvent> { event ->
            when (mode) {
                Mode.Vanilla -> event.cancel()
                Mode.Legit -> runSafe {
                    if (player.isMoving) shouldReSprint = true
                }
            }
        }

        nonNullHandler<AfterSendPositionEvent> {
            if (shouldReSprint && player.isMoving && player.foodData.foodLevel > 6 && !player.isBlocking) {
                shouldReSprint = false
                player.isSprinting = true
            }
        }
    }

    override fun onDisable() {
        shouldReSprint = false
    }

}
