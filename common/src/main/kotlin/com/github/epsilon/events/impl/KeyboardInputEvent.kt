package com.github.epsilon.events.impl

import net.minecraft.world.entity.player.Input

class KeyboardInputEvent(
    var forward: Float,
    var strafe: Float,
    var jump: Boolean,
    var sneak: Boolean,
    var sprint: Boolean,
) {

    fun toNewInput(): Input {
        return Input(
            forward > 0.0f,
            forward < 0.0f,
            strafe > 0.0f,
            strafe < 0.0f,
            jump,
            sneak,
            sprint
        )
    }

}
