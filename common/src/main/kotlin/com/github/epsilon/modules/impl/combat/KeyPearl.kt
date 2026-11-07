package com.github.epsilon.modules.impl.combat

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.KeyPressEvent
import com.github.epsilon.events.impl.MousePressEvent
import com.github.epsilon.kernel.jvm.*
import com.github.epsilon.managers.NotificationManager
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.movement.NoSlowdown
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.player.InvUtils.findInHotbar
import com.github.epsilon.utils.player.InvUtils.swap
import com.github.epsilon.utils.player.InvUtils.swapBack
import com.github.epsilon.utils.player.PlayerUtils.isEating
import com.github.epsilon.utils.player.SwingMode
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rotation
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Items

object KeyPearl : Module("Key Pearl", Category.COMBAT) {

    private val activateKey by keybindSetting("Activate Key", InputConstants.UNKNOWN.value)
    private val pauseOnEat by boolSetting("Pause On Eat", true)
    private val delay by intSetting("Delay", 0, 0..20, 1)
    private val switchBack by boolSetting("Switch Back", true)
    private val switchDelay by intSetting("Switch Delay", 0, 0..20, 1)
    private val swingHand by boolSetting("Swing Hand", true)

    private var pressed = false
    private var hasActivated = false
    private var clock = 0
    private var switchClock = 0

    init {
        onEnable {
            resetState()
            pressed = false
        }

        onDisable {
            resetState()
            pressed = false
        }

        handler<KeyPressEvent> { event ->
            if (event.getKey() == activateKey) {
                if (event.action == InputConstants.RELEASE) {
                    pressed = false
                } else if (mc.gui.screen() == null) {
                    pressed = true
                }
            }
        }

        handler<MousePressEvent> { event ->
            if (KeybindUtils.encodeMouseButton(event.button) == activateKey) {
                if (event.action == InputConstants.RELEASE) {
                    pressed = false
                } else if (mc.gui.screen() == null) {
                    pressed = true
                }
            }
        }

        onUpdate {
            if (mc.gui.screen() != null) {
                pressed = false
                return@onUpdate
            }

            if (hasActivated) {
                if (switchBack) {
                    handleSwitchBack()
                } else {
                    resetState()
                }
            }

            if (pressed) {
                val pearl = findInHotbar(Items.ENDER_PEARL)
                if (!pearl.found()) {
                    resetState()
                    return@onUpdate
                }

                if (player.cooldowns.isOnCooldown(Items.ENDER_PEARL.defaultInstance)) {
                    return@onUpdate
                }

                if (pauseOnEat && (isEating() || NoSlowdown.isWorking())) {
                    return@onUpdate
                } else {
                    if (NoSlowdown.isWorking()) {
                        NotificationManager.warning(
                            NoSlowdown.translatedName,
                            EpsilonTranslations.Notifications.NO_SLOWDOWN_DISABLED_PEARL.getTranslatedName()
                        )
                        NoSlowdown.stop()
                    }
                }

                // 强制给你的转头归位
                RotationManager.setRotations(
                    Rotation(player.yRot, player.xRot), 180.0, Priority.Highest
                )

                swap(pearl.slot, switchBack)

                if (clock < delay) {
                    clock++
                    return@onUpdate
                }

                if (!hasActivated) {
                    val result: InteractionResult = gameMode.useItem(player, InteractionHand.MAIN_HAND)
                    if (result.consumesAction()) {
                        SwingMode.of(swingHand).interactSwing(InteractionHand.MAIN_HAND)
                    }
                    hasActivated = true
                }
            }
        }
    }

    private fun NonNullContext.handleSwitchBack() {
        if (switchClock < switchDelay) {
            switchClock++
            return
        }
        swapBack()
        resetState()
    }

    private fun resetState() {
        clock = 0
        switchClock = 0
        hasActivated = false
    }

}
