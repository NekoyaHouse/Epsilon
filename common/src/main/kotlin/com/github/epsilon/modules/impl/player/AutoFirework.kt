package com.github.epsilon.modules.impl.player

import com.github.epsilon.utils.*
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.player.InvUtils
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object AutoFirework : Module("Auto Firework", Category.PLAYER) {

    private val activateKey by keybindSetting("Activate Key", -1)
    private val delay by intSetting("Delay", 0, 0, 20, 1)
    private val switchBack by boolSetting("Switch Back", true)
    private val switchDelay by intSetting("Switch Delay", 0, 0, 20, 1)

    private var hasUsedFirework = false
    private var useDelayCounter = 0
    private var previousSelectedSlot = -1
    private var switchDelayCounter = 0
    private var cooldownCounter = 0
    private var previousItem: ItemStack? = null
    private var isKeyDown = false

    init {
        nonNullListener<PlayerTickEvent.Pre> {
            val pressed = KeybindUtils.isPressed(activateKey)
            if (!pressed) {
                isKeyDown = false
            }

            if (cooldownCounter > 0) {
                --cooldownCounter
                return@nonNullListener
            }

            if (pressed) {
                if (isKeyDown && previousSelectedSlot == -1) return@nonNullListener
                isKeyDown = true

                if (previousSelectedSlot == -1) {
                    previousSelectedSlot = player.inventory.selectedSlot
                    previousItem = player.mainHandItem
                }

                val fireworkSlot = InvUtils.findInHotbar(Items.FIREWORK_ROCKET).slot
                if (fireworkSlot == -1) {
                    resetState()
                    return@nonNullListener
                }

                InvUtils.swap(fireworkSlot, false)

                if (useDelayCounter < delay) {
                    ++useDelayCounter
                    return@nonNullListener
                }

                if (!hasUsedFirework) {
                    interaction.useItem(player, InteractionHand.MAIN_HAND)
                    hasUsedFirework = true
                    if (!switchBack) {
                        resetState()
                    }
                }

                if (hasUsedFirework && switchBack) {
                    handleSwitchBack()
                }
            }
        }
    }

    override fun onEnable() {
        resetState()
        isKeyDown = false
    }

    context(nn: NonNullContext)
    private fun handleSwitchBack() {
        if (switchDelayCounter < switchDelay) {
            ++switchDelayCounter
            return
        }

        if (previousSelectedSlot != -1) {
            InvUtils.swap(previousSelectedSlot, false)
        }

        resetState()
    }

    private fun resetState() {
        previousSelectedSlot = -1
        previousItem = null
        useDelayCounter = 0
        switchDelayCounter = 0
        cooldownCounter = 4
        hasUsedFirework = false
    }

}
