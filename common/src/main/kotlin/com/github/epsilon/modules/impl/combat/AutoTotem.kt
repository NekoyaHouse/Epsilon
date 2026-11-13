package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.kernel.gameplay.inventory.ContainerClicks.click
import com.github.epsilon.kernel.gameplay.inventory.ContainerClicks.swap
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getItemCount
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getItemSlot
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

object AutoTotem : Module("Auto Totem", Category.COMBAT) {

    private val strict by boolSetting("Strict", true)
    private val health by doubleSetting("Health", 16.0, 0.0..36.0, 0.5)
    private val checkGapple by boolSetting("Check Gapple", true)

    init {
        safeEventHandler<PlayerTickEvent.Pre> {
            if (!shouldHoldTotem()) {
                return@safeEventHandler
            }

            if (player.offhandItem.`is`(Items.TOTEM_OF_UNDYING)) {
                return@safeEventHandler
            }

            val slot = getItemSlot(Items.TOTEM_OF_UNDYING)
            if (slot == -1) {
                return@safeEventHandler
            }

            moveItemToOffhand(slot)
        }
    }

    override fun getInfo(): String? = runSafe { getItemCount(Items.TOTEM_OF_UNDYING).toString() }

    private fun NonNullContext.shouldHoldTotem(): Boolean {
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

    private fun NonNullContext.moveItemToOffhand(slot: Int) {
        var target = slot
        if (target < 9) {
            target += 36
        }

        if (!strict) {
            swap(target, 40)
            return
        }

        click(target)
        click(45)

        if (!player.inventoryMenu.carried.isEmpty) {
            click(target)
        }
    }

}
