package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.AfterSendPositionEvent
import com.github.epsilon.events.impl.StartDestroyBlockEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.player.EnchantmentUtils
import com.github.epsilon.utils.player.InvHelper
import net.minecraft.core.BlockPos
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.DropExperienceBlock
import net.minecraft.world.level.block.RedStoneOreBlock
import net.minecraft.world.level.block.WebBlock

object AutoTool : Module("Auto Tool", Category.PLAYER) {

    private val swapBack by boolSetting("Swap Back", true)
    private val swapBackDelay by intSetting("Swap Back Delay", 0, 0, 15, 1, dependency = { swapBack })

    private var oldSlot = -1
    private var lastDestroyTick = -1

    init {
        nonNullHandler<StartDestroyBlockEvent>(EventPriority.LOWEST) { event ->
            lastDestroyTick = player.tickCount
            val bestTool = getBestTool(event.blockPos)
            val selectedSlot = player.inventory.selectedSlot
            if (bestTool == -1 || bestTool == selectedSlot) return@nonNullHandler
            if (swapBack) {
                if (oldSlot == -1) oldSlot = selectedSlot
            } else {
                oldSlot = -1
            }
            player.inventory.selectedSlot = bestTool
            interaction.ensureHasSentCarriedItem()
        }

        nonNullHandler<AfterSendPositionEvent> {
            if (oldSlot == -1) return@nonNullHandler

            if (interaction.isDestroying || BedNuker.isBreakingTarget) {
                lastDestroyTick = player.tickCount
                return@nonNullHandler
            }

            if (player.tickCount - lastDestroyTick >= swapBackDelay) {
                player.inventory.selectedSlot = oldSlot
                oldSlot = -1
            }
        }
    }

    override fun onDisable() {
        runSafe {
            if (oldSlot != -1) {
                player.inventory.selectedSlot = oldSlot
            }
        }
        oldSlot = -1
        lastDestroyTick = -1
    }

    fun getBestTool(pos: BlockPos): Int {
        val nn = NonNullContext.current ?: return -1
        return context(nn) {
            val blockState = level.getBlockState(pos)
            val block = blockState.block
            var slot = 0
            var dmg = 1.0F

            for (index in 0 until 9) {
                val itemStack = player.inventory.getItem(index)
                if (
                    !InvHelper.isGodItem(itemStack) &&
                    !itemStack.isEmpty &&
                    !blockState.isAir &&
                    (!itemStack.`is`(ItemTags.SWORDS) || block is WebBlock)
                ) {
                    var strVsBlock = itemStack.item.getDestroySpeed(itemStack, blockState)
                    if (strVsBlock > 1.0F && block !is DropExperienceBlock && block !is RedStoneOreBlock) {
                        val level = EnchantmentUtils.getEnchantmentLevel(itemStack, Enchantments.EFFICIENCY)
                        if (level > 0) {
                            strVsBlock += (level * level + 1).toFloat()
                        }
                    }

                    if (strVsBlock > dmg) {
                        slot = index
                        dmg = strVsBlock
                    }
                }
            }

            if (dmg > 1.0F) slot else -1
        }
    }

}
