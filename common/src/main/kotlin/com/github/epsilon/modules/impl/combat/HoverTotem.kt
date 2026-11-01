package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.player.ClickSlotUtils
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.item.Items
import java.util.*
import kotlin.math.ceil

object HoverTotem : Module("Hover Totem", Category.COMBAT) {

    private val delay by doubleSetting("Delay", 0.0, 0.0..20.0, 0.1)
    private val randomDelay by doubleSetting("Random Delay", 0.0, 0.0..10.0, 0.1)
    private val hotbar by boolSetting("Hotbar", true)
    private val slot by doubleSetting("Totem Slot", 1.0, 1.0..9.0, 1.0)
    private val autoSwitch by boolSetting("Auto Switch", true)

    private var clock = 0
    private val random = Random()
    private var currentDelay = 0

    init {
        onEnable {
            clock = 0
            currentDelay = 0
        }

        safeEventHandler<PlayerTickEvent.Pre> {
            val screen = mc.gui.screen()
            if (screen is InventoryScreen) {
                val hoveredSlot = screen.hoveredSlot

                if (autoSwitch) {
                    val slotValue = slot.toInt()
                    player.inventory.selectedSlot = slotValue - 1
                }

                if (hoveredSlot != null) {
                    val slotIndex = hoveredSlot.index
                    if (slotIndex > 35) {
                        return@safeEventHandler
                    }

                    val totemSlot = slot.toInt()
                    val totem = totemSlot - 1

                    if (hoveredSlot.item.`is`(Items.TOTEM_OF_UNDYING)) {
                        if (hotbar && !player.inventory.getItem(totem).`is`(Items.TOTEM_OF_UNDYING)) {
                            if (clock > 0) {
                                --clock
                                return@safeEventHandler
                            }
                            ClickSlotUtils.swap(player.containerMenu.containerId, slotIndex, totem)
                            currentDelay = getRandomDelay()
                            clock = currentDelay
                        } else if (!player.offhandItem.`is`(Items.TOTEM_OF_UNDYING)) {
                            if (clock > 0) {
                                --clock
                                return@safeEventHandler
                            }
                            ClickSlotUtils.swap(player.containerMenu.containerId, slotIndex, 40)
                            currentDelay = getRandomDelay()
                            clock = currentDelay
                        }
                    }
                }
            } else {
                currentDelay = getRandomDelay()
                clock = currentDelay
            }
        }
    }

    private fun getRandomDelay(): Int {
        val baseDelay = delay.toInt()
        val randomDelayValue = randomDelay

        if (randomDelayValue <= 0.0) {
            return baseDelay
        }

        val randomValue = random.nextDouble() * randomDelayValue
        val totalDelay = baseDelay + randomValue

        return ceil(totalDelay).toInt()
    }

}
