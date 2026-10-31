package com.github.epsilon.modules.impl.player

import com.github.epsilon.Constants
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.events.impl.SendPositionEvent
import com.github.epsilon.managers.NotificationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.movement.NoSlowdown
import com.github.epsilon.utils.network.NetworkUtils
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

object NoFall : Module("No Fall", Category.PLAYER) {

    private enum class Mode {
        GroundSpoof,
        GrimJump,
    }

    private val mode by enumSetting("Mode", Mode.GroundSpoof)
    private val fallDistance by doubleSetting("Fall Distance", 3.0, 3.0..15.0, 0.1)

    private var jump = false
    private var lastFallDistance = 0.0

    init {
        safeEventHandler<SendPositionEvent> { event ->
            when (mode) {
                Mode.GroundSpoof -> {
                    if (player.fallDistance > fallDistance) event.isOnGround = true
                }

                Mode.GrimJump -> {
                    val oldFallDistance = player.fallDistance

                    if (lastFallDistance >= fallDistance) {
                        val noSlowdown = NoSlowdown
                        if (NoSlowdown.isWorking()) {
                            NotificationManager.warning(
                                noSlowdown.translatedName,
                                EpsilonTranslations.Notifications.NO_SLOWDOWN_DISABLED_FALLING.getTranslatedName()
                            )
                            noSlowdown.stop()
                        }

                        if (event.isOnGround) {
                            event.isOnGround = false
                            NetworkUtils.sendPacketNoEvent(
                                ServerboundMovePlayerPacket.StatusOnly(
                                    true,
                                    player.horizontalCollision
                                )
                            )
                            Constants.skipTicks++
                            jump = true
                        }
                    }

                    lastFallDistance = oldFallDistance
                }
            }
        }

        safeEventHandler<KeyboardInputEvent> { event ->
            if (mode != Mode.GrimJump) return@safeEventHandler

            if (lastFallDistance >= fallDistance && player.onGround()) {
                event.sneak = false
            }

            if (jump) {
                event.jump = true
                jump = false
            }
        }
    }

    override fun onEnable() {
        lastFallDistance = 0.0
    }

    override fun onDisable() {
        lastFallDistance = 0.0
    }

}
