package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.safety.*

object JumpReset : Module("Jump Reset", Category.MOVEMENT) {

    init {
        nonNullHandler<KeyboardInputEvent> { event ->
            if (player.onGround() && player.hurtTime == 9) {
                event.isJump = true
            }
        }
    }

}
