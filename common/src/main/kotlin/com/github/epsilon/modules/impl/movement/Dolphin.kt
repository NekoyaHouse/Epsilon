package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.player
import net.minecraft.world.phys.Vec3

object Dolphin : Module("Dolphin", Category.MOVEMENT) {

    init {
        nonNullListener<PlayerTickEvent.Pre> {
            if (!player.isInWater || player.isShiftKeyDown) return@nonNullListener

            val velocity = player.deltaMovement
            player.deltaMovement = Vec3(velocity.x, velocity.y + 0.04, velocity.z)
        }
    }

}
