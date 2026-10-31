package com.github.epsilon.modules.impl.movement

import com.github.epsilon.utils.*
import com.github.epsilon.events.impl.SendPositionEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.player.MoveUtils
import net.minecraft.world.phys.Vec3

object Flight : Module("Flight", Category.MOVEMENT) {

    private val stopOnDisable by boolSetting("Stop On Disable", true)
    private val horizontalSpeed by doubleSetting("Horizontal Speed", 3.5, 0.1, 10.0, 0.1)
    private val verticalSpeed by doubleSetting("Vertical Speed", 1.0, 0.1, 5.0, 0.1)

    init {
        nonNullListener<SendPositionEvent> {
            var y = 0.0

            if (mc.options.keyJump.isDown) {
                y = verticalSpeed
            } else if (mc.options.keyShift.isDown) {
                y = -verticalSpeed
            }

            val strafe = forward(horizontalSpeed)
            player.deltaMovement = Vec3(strafe[0], y, strafe[1])
        }
    }

    override fun onDisable() {
        runSafe {
            if (stopOnDisable) player.deltaMovement = Vec3(0.0, 0.0, 0.0)
        }
    }

}
