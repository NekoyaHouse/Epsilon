package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting
import net.minecraft.world.entity.player.Input

class KeyboardInputEvent(
    var forward: Float,
    var strafe: Float,
    var jump: Boolean,
    var sneak: Boolean,
    var sprint: Boolean,
) : IEvent<KeyboardInputEvent>, IPosting by Companion {
    companion object : EventBus()

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
