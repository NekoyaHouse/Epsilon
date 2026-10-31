package com.github.epsilon.modules.impl.combat

import com.github.epsilon.utils.*
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.NonNullContext
import com.github.epsilon.utils.player
import com.github.epsilon.utils.player.ClickSlotUtils
import com.github.epsilon.utils.player.InvHelper
import com.github.epsilon.utils.runSafe
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

object AutoTotem : Module("Auto Totem", Category.COMBAT) {

    private val strict by boolSetting("Strict", true)
    private val health by doubleSetting("Health", 16.0, 0.0, 36.0, 0.5)
    private val checkGapple by boolSetting("Check Gapple", true)

    init {
        nonNullListener<PlayerTickEvent.Pre> {
            if (!shouldHoldTotem()) {
                return@nonNullListener
            }

            if (player.offhandItem.`is`(Items.TOTEM_OF_UNDYING)) {
                return@nonNullListener
            }

            val slot = getItemSlot(Items.TOTEM_OF_UNDYING)
            if (slot == -1) {
                return@nonNullListener
            }

            moveItemToOffhand(slot)
        }
    }

    override fun getInfo(): String? {
        val nn = NonNullContext.current ?: return null
        return context(nn) { InvHelper.getItemCount(Items.TOTEM_OF_UNDYING).toString() }
    }

    context(nn: NonNullContext)
    private fun shouldHoldTotem(): Boolean {
        val player = nn.player
        val totalHealth = player.health + player.absorptionAmount

        if (totalHealth <= health.toFloat()) {
            return true
        }

        // Keep an empty offhand safe by filling it with a totem.
        if (player.offhandItem.isEmpty) {
            return true
        }

        // Simple void safety check for modern overworld min Y.
        if (player.y < -64.0) {
            return true
        }

        if (checkGapple) {
            val mainHandItem: Item = player.mainHandItem.item
            if (mainHandItem === Items.GOLDEN_APPLE || mainHandItem === Items.ENCHANTED_GOLDEN_APPLE) {
                return true
            }
        }

        return false
    }

    context(nn: NonNullContext)
    private fun moveItemToOffhand(slot: Int) {
        var target = slot
        if (target < 9) {
            target += 36
        }

        if (!strict) {
            ClickSlotUtils.swap(target, 40)
            return
        }

        ClickSlotUtils.click(target)
        ClickSlotUtils.click(45)

        if (!nn.player.inventoryMenu.carried.isEmpty) {
            ClickSlotUtils.click(target)
        }
    }

}
