package com.github.epsilon.modules.impl.player

import com.github.epsilon.utils.*
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.player.ClickSlotUtils
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.PlayerUtils
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.function.Predicate

object ElytraSwap : Module("Elytra Swap", Category.PLAYER) {

    private val activateKey by keybindSetting("Activate Key", InputConstants.KEY_G)
    private val swapDelay by intSetting("Delay", 0, 0, 20, 1)
    private val switchBack by boolSetting("Switch Back", true)
    private val switchDelay by intSetting("Switch Delay", 0, 0, 20, 1)
    private val moveToSlot by boolSetting("Move to slot", true)
    private val elytraSlotSetting by intSetting("Elytra Slot", 9, 1, 9, 1)

    private var isSwinging = false
    private var isItemSwapped = false
    private var swapCounter = 0
    private var switchCounter = 0
    private var originalSlot = -1
    private var isKeyDown = false

    init {
        nonNullListener<PlayerTickEvent.Pre> {
            if (mc.gui.screen() != null) return@nonNullListener

            val pressed = isPressed(activateKey)
            if (!pressed) {
                isKeyDown = false
            }

            if (pressed) {
                if (isKeyDown && originalSlot == -1) return@nonNullListener
                isKeyDown = true

                if (originalSlot == -1) {
                    originalSlot = player.inventory.selectedSlot
                }

                if (swapCounter < swapDelay) {
                    ++swapCounter
                    return@nonNullListener
                }

                val wearingElytra = player.getItemBySlot(EquipmentSlot.CHEST).`is`(Items.ELYTRA)

                val predicate: Predicate<ItemStack> = if (wearingElytra) {
                    Predicate { stack ->
                        if (stack.isEmpty) {
                            return@Predicate false
                        }
                        val equippable = stack.get(DataComponents.EQUIPPABLE)
                        equippable != null && equippable.slot() == EquipmentSlot.CHEST
                    }
                } else {
                    Predicate { stack -> stack.`is`(Items.ELYTRA) }
                }
                if (!isItemSwapped) {
                    val targetSlot = findInHotbar(predicate).slot

                    if (targetSlot == -1) {
                        if (!moveToSlot) {
                            resetState()
                            return@nonNullListener
                        }

                        val invSlot = find(predicate).slot
                        if (invSlot != -1) {
                            var containerSlot = invSlot
                            if (invSlot < 9) containerSlot += 36

                            val targetHotbarSlot = elytraSlotSetting - 1

                            swap(player.containerMenu.containerId, containerSlot, targetHotbarSlot)
                            swapCounter = 0
                            return@nonNullListener
                        } else {
                            resetState()
                            return@nonNullListener
                        }
                    }

                    swap(targetSlot, false)
                    isItemSwapped = true
                }

                if (!isSwinging) {
                    interaction.useItem(player, InteractionHand.MAIN_HAND)
                    swingHand(InteractionHand.MAIN_HAND)
                    isSwinging = true
                }

                if (switchBack) {
                    handleSwitchBack()
                } else {
                    resetState()
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
        if (switchCounter < switchDelay) {
            ++switchCounter
            return
        }

        if (originalSlot != -1) {
            InvUtils.swap(originalSlot, false)
        }
        resetState()
    }

    private fun resetState() {
        originalSlot = -1
        switchCounter = 0
        swapCounter = 0
        isSwinging = false
        isItemSwapped = false
    }

}
