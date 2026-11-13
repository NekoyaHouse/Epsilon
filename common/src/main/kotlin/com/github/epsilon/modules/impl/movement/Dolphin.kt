package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import net.minecraft.world.phys.Vec3

object Dolphin : Module("Dolphin", Category.MOVEMENT) {

    init {
        safeEventHandler<PlayerTickEvent.Pre> {
            if (!player.isInWater || player.isShiftKeyDown) return@safeEventHandler

            val velocity = player.deltaMovement
            player.deltaMovement = Vec3(velocity.x, velocity.y + 0.04, velocity.z)
        }
    }

}
