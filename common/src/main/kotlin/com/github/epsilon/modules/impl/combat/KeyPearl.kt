package com.github.epsilon.modules.impl.combat

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.KeyPressEvent
import com.github.epsilon.events.impl.MousePressEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.managers.NotificationManager
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.movement.NoSlowdown
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.player.SwingMode
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rot2f
import com.github.epsilon.utils.safety.NonNullContext
import com.github.epsilon.utils.safety.interaction
import com.github.epsilon.utils.safety.player
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Items

object KeyPearl : Module("Key Pearl", Category.COMBAT) {

    private val activateKey by keybindSetting("Activate Key", InputConstants.UNKNOWN.value)
    private val pauseOnEat by boolSetting("Pause On Eat", true)
    private val delay by intSetting("Delay", 0, 0, 20, 1)
    private val switchBack by boolSetting("Switch Back", true)
    private val switchDelay by intSetting("Switch Delay", 0, 0, 20, 1)
    private val swingHand by boolSetting("Swing Hand", true)

    private var pressed = false
    private var hasActivated = false
    private var clock = 0
    private var switchClock = 0

    init {
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

        nonNullHandler<PlayerTickEvent.Pre> {
            if (mc.gui.screen() != null) {
                pressed = false
                return@nonNullHandler
            }

            if (hasActivated) {
                if (switchBack) {
                    handleSwitchBack()
                } else {
                    resetState()
                }
            }

            if (pressed) {
                val pearl = InvUtils.findInHotbar(Items.ENDER_PEARL)
                if (!pearl.found()) {
                    resetState()
                    return@nonNullHandler
                }

                if (player.cooldowns.isOnCooldown(Items.ENDER_PEARL.defaultInstance)) {
                    return@nonNullHandler
                }

                if (pauseOnEat && (PlayerUtils.isEating() || NoSlowdown.isWorking())) {
                    return@nonNullHandler
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
                RotationManager.INSTANCE.setRotations(
                    Rot2f(player.yRot, player.xRot), 180.0, Priority.Highest
                )

                InvUtils.swap(pearl.slot, switchBack)

                if (clock < delay) {
                    clock++
                    return@nonNullHandler
                }

                if (!hasActivated) {
                    val result: InteractionResult = interaction.useItem(player, InteractionHand.MAIN_HAND)
                    if (result.consumesAction()) {
                        SwingMode.of(swingHand).interactSwing(InteractionHand.MAIN_HAND)
                    }
                    hasActivated = true
                }
            }
        }
    }

    override fun onEnable() {
        resetState()
        pressed = false
    }

    override fun onDisable() {
        resetState()
        pressed = false
    }

    context(nn: NonNullContext)
    private fun handleSwitchBack() {
        if (switchClock < switchDelay) {
            switchClock++
            return
        }
        InvUtils.swapBack()
        resetState()
    }

    private fun resetState() {
        clock = 0
        switchClock = 0
        hasActivated = false
    }

}
