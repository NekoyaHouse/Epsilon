package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module

object JumpReset : Module("Jump Reset", Category.MOVEMENT) {

    init {
        safeEventHandler<KeyboardInputEvent> { event ->
            if (player.onGround() && player.hurtTime == 9) {
                event.jump = true
            }
        }
    }

}
