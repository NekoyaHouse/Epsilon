package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.events.impl.MoveEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.player.MoveUtils
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.safety.player
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.phys.Vec3

object Strafe : Module("Strafe", Category.MOVEMENT) {

    private val airStop by boolSetting("Air Stop", true)
    private val autoJump by boolSetting("Auto Jump", true)

    init {
        nonNullHandler<MoveEvent> { event ->
            if (player.isCrouching || player.isFallFlying || player.isInLava ||
                PlayerUtils.isInBlock() || player.isInWater || player.abilities.flying ||
                Flight.isEnabled || Speed.isEnabled
            ) {
                return@nonNullHandler
            }

            if (airStop && !player.isMoving) {
                player.deltaMovement = Vec3(0.0, player.deltaMovement.y, 0.0)
                return@nonNullHandler
            }

            var speed = 0.2873

            if (player.hasEffect(MobEffects.SPEED) && !player.hasEffect(MobEffects.SLOWNESS)) {
                speed *= 1.0 + 0.2 * (player.getEffect(MobEffects.SPEED)!!.amplifier + 1.0)
            }

            val strafe = MoveUtils.forward(speed)
            event.x = strafe[0]
            event.z = strafe[1]
            event.cancel()
        }

        nonNullHandler<KeyboardInputEvent> { event ->
            if (autoJump && player.isMoving) event.jump = true
        }
    }

}
