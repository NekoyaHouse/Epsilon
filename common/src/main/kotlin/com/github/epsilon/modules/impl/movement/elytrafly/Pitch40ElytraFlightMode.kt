package com.github.epsilon.modules.impl.movement.elytrafly

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.events.impl.FallFlyingEvent
import com.github.epsilon.events.impl.FireworkRotationEvent
import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.managers.NotificationManager
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rot2f
import com.github.epsilon.utils.safety.NonNullContext
import com.github.epsilon.utils.safety.player
import com.github.epsilon.utils.safety.runSafe
import com.github.epsilon.utils.safety.runSafeOrFalse
import com.github.epsilon.utils.timer.TimerUtils
import net.minecraft.world.item.Items
import kotlin.random.Random

class Pitch40ElytraFlightMode(elytraFly: ElytraFly) : ElytraFlightMode(elytraFly) {

    private var pitchingDown = true
    private var pendingActivationCheck = false
    private var takingOff = false
    private var shouldJump = false
    private var packetDelayTicks = 0
    private var completedTakeoffTarget = Double.NaN
    private var pitch = DESCEND_PITCH
    private val fireworkTimer = TimerUtils()

    override fun onEnable() {
        resetState()

        if (NonNullContext.current == null) {
            pendingActivationCheck = true
            return
        }

        initializeInWorld()
    }

    private fun resetState() {
        pitchingDown = true
        pendingActivationCheck = false
        takingOff = false
        shouldJump = false
        packetDelayTicks = 0
        completedTakeoffTarget = Double.NaN
        pitch = DESCEND_PITCH
        fireworkTimer.setMs(917813L)
    }

    private fun initializeInWorld() {
        runSafe {
            val targetHeight = getTakeoffTargetHeight()
            if (shouldStartTakeoff(targetHeight)) {
                startTakeoff()
                return@runSafe
            }
            if (elytraFly.pitch40AutoTakeoff) {
                completedTakeoffTarget = targetHeight
            }

            if (player.y - 40.0 < elytraFly.pitch40lowerBounds) {
                fail(EpsilonTranslations.ElytraFly.PITCH40_TOO_CLOSE_TO_LOWER_BOUNDS)
            }
        }
    }

    override fun onDisable() {
        pendingActivationCheck = false
        takingOff = false
        shouldJump = false
        packetDelayTicks = 0
        completedTakeoffTarget = Double.NaN
    }

    override fun onPlayerTick() {
        runSafe {
            if (pendingActivationCheck) {
                pendingActivationCheck = false
                initializeInWorld()
                if (!elytraFly.isEnabled) return@runSafe
            }

            if (takingOff) {
                updateTakeoff()
                if (elytraFly.isEnabled) {
                    redirectRotation()
                }
                return@runSafe
            }

            if (player.onGround()) {
                completedTakeoffTarget = Double.NaN
            }

            if (shouldStartTakeoff(getTakeoffTargetHeight())) {
                startTakeoff()
                updateTakeoff()
                if (elytraFly.isEnabled) {
                    redirectRotation()
                }
                return@runSafe
            }

            maintainFallFlying()
            updatePitch()
            redirectRotation()
        }
    }

    override fun onKeyboardInput(event: KeyboardInputEvent) {
        if (shouldJump) {
            event.isJump = true
            shouldJump = false
        }
    }

    override fun shouldCancelRightClick(): Boolean {
        return false
    }

    override fun onFallFlying(event: FallFlyingEvent) {
        event.yaw = elytraFly.getPitch40Yaw(event.yaw)
        event.pitch = pitch
    }

    override fun onFireworkUpdate(event: FireworkRotationEvent) {
        runSafe {
            event.yaw = elytraFly.getPitch40Yaw(player.yRot)
            event.pitch = pitch
        }
    }

    private fun updateTakeoff() {
        runSafe {
            pitch = TAKEOFF_PITCH

            if (player.y >= getTakeoffTargetHeight()) {
                finishTakeoff()
                return@runSafe
            }

            val elytra = InvUtils.find(Items.ELYTRA)
            if (!canGlide(elytra.found())) {
                fail(EpsilonTranslations.ElytraFly.PITCH40_NO_USABLE_ELYTRA)
                return@runSafe
            }

            redirectRotation()

            if (player.onGround()) {
                shouldJump = true
                return@runSafe
            }

            if (elytraFly.armored) {
                refreshArmoredFallFlying(elytra.slot, false)
            } else if (canStartFallFlying() && startFallFlying()) {
                shouldJump = true
            }

            if (elytraFly.pitch40AutoFirework && player.isFallFlying &&
                fireworkTimer.hasDelayed(elytraFly.pitch40FireworkCooldown) && useFirework()
            ) {
                fireworkTimer.reset()
            }
        }
    }

    private fun maintainFallFlying() {
        runSafe {
            if (player.onGround() || player.isInWater) return@runSafe

            val elytra = InvUtils.find(Items.ELYTRA)
            if (!canGlide(elytra.found())) return@runSafe

            if (elytraFly.armored) {
                refreshArmoredFallFlying(elytra.slot, true)
            } else if (startFallFlying()) {
                shouldJump = true
            }
        }
    }

    private fun redirectRotation() {
        runSafe {
            RotationManager.INSTANCE.setRotations(
                Rot2f(elytraFly.getPitch40Yaw(player.yRot), pitch),
                180.0,
                Priority.Highest
            )
        }
    }

    private fun finishTakeoff() {
        takingOff = false
        pitchingDown = true
        packetDelayTicks = 0
        completedTakeoffTarget = getTakeoffTargetHeight()
        pitch = DESCEND_PITCH
        maintainFallFlying()
        NotificationManager.success(
            elytraFly.translatedName,
            EpsilonTranslations.ElytraFly.PITCH40_TAKEOFF_COMPLETE.getTranslatedName()
        )
    }

    private fun refreshArmoredFallFlying(elytraSlot: Int, respectDelay: Boolean) {
        runSafe {
            if (respectDelay && ++packetDelayTicks <= elytraFly.pitch40PacketDelay) {
                return@runSafe
            }
            packetDelayTicks = 0

            val elytra = if (elytraSlot < 9) elytraSlot + 36 else elytraSlot
            swapArmor(elytra)
            if (!player.isFallFlying && startFallFlying()) {
                shouldJump = true
            } else if (player.isFallFlying) {
                restartFallFlying()
            }
            swapArmor(elytra)
        }
    }

    private fun updatePitch() {
        runSafe {
            /*
             * 下降时看向 37.72 度；上升时先快速抬头到 -54.77 度，再缓慢压回 37.72 度。
             */
            if (pitchingDown && player.y <= elytraFly.pitch40lowerBounds) {
                pitchingDown = false
            }

            if (!pitchingDown) {
                pitch -= randPitch(elytraFly.pitch40rotationSpeedUp.toFloat(), 1.0f)

                if (pitch < ASCEND_PITCH) {
                    pitch = ASCEND_PITCH
                    pitchingDown = true
                }
            } else if (pitch < DESCEND_PITCH) {
                pitch += randPitch(elytraFly.pitch40rotationSpeedDown.toFloat(), 0.50f)
            }
        }
    }

    private fun getTakeoffTargetHeight(): Double {
        return elytraFly.pitch40TakeoffTargetHeight
    }

    private fun startTakeoff() {
        takingOff = true
        pitch = TAKEOFF_PITCH
    }

    private fun shouldStartTakeoff(targetHeight: Double): Boolean = runSafeOrFalse {
        elytraFly.pitch40AutoTakeoff &&
                player.y < targetHeight - TARGET_REACH_MARGIN &&
                (completedTakeoffTarget.isNaN() || targetHeight > completedTakeoffTarget + TARGET_REACH_MARGIN)
    }

    private fun fail(message: TranslateComponent) {
        NotificationManager.error(elytraFly.translatedName, message.getTranslatedName())
        elytraFly.toggle()
    }

    private fun randPitch(pitch: Float, bound: Float): Float {
        return (pitch + bound * (Random.nextDouble() - 0.5)).toFloat()
    }

    private companion object {

        private const val DESCEND_PITCH = 37.72f
        private const val ASCEND_PITCH = -54.77f
        private const val TAKEOFF_PITCH = ASCEND_PITCH
        private const val TARGET_REACH_MARGIN = 4.0

    }

}
