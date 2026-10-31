package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.safety.*
import net.minecraft.util.Mth

object MovementFix : Module("Movement Fix", Category.MOVEMENT) {

    init {
        setDefaultEnabled(true)
    }

    private fun getDirection(yaw: Float, forward: Float, strafe: Float): Float {
        val direction = yaw

        val isMovingForward = forward > 0.0f
        val isMovingBack = forward < 0.0f
        val isMovingRight = strafe > 0.0f
        val isMovingLeft = strafe < 0.0f
        val isMovingSideways = isMovingRight || isMovingLeft
        val isMovingStraight = isMovingForward || isMovingBack

        if (forward != 0.0f || strafe != 0.0f) {
            if (isMovingBack && !isMovingSideways) {
                return direction + 180.0f
            }
            if (isMovingForward && isMovingLeft) {
                return direction + 45.0f
            }
            if (isMovingForward && isMovingRight) {
                return direction - 45.0f
            }
            if (!isMovingStraight && isMovingLeft) {
                return direction + 90.0f
            }
            if (!isMovingStraight && isMovingRight) {
                return direction - 90.0f
            }
            if (isMovingBack && isMovingLeft) {
                return direction + 135.0f
            }
            if (isMovingBack) {
                return direction - 135.0f
            }
        }

        return direction
    }

    fun fixMovement(event: KeyboardInputEvent, yaw: Float) {
        runSafe {
            var forward = event.forward
            var strafe = event.strafe

            val angleUnit = 45
            val angleTolerance = 22.5f
            val directionFactor = Math.max(Math.abs(forward), Math.abs(strafe))
            val angleDifference = Mth.wrapDegrees(getDirection(player.yRot, forward, strafe) - yaw).toDouble()
            val angleDistance = Math.abs(angleDifference)

            forward = 0.0f
            strafe = 0.0f

            if (angleDistance <= (angleUnit.toFloat() + angleTolerance).toDouble()) {
                forward++
            } else if (angleDistance >= (180.0f - angleUnit.toFloat() - angleTolerance).toDouble()) {
                forward--
            }

            if (angleDifference >= (angleUnit.toFloat() - angleTolerance).toDouble() &&
                angleDifference <= (180.0f - angleUnit.toFloat() + angleTolerance).toDouble()
            ) {
                strafe--
            } else if (angleDifference <= (-angleUnit.toFloat() + angleTolerance).toDouble() &&
                angleDifference >= (-180.0f + angleUnit.toFloat() - angleTolerance).toDouble()
            ) {
                strafe++
            }

            forward *= directionFactor
            strafe *= directionFactor

            event.forward = forward
            event.strafe = strafe
        }
    }

}
