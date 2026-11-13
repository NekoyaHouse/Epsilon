package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.kernel.gameplay.inventory.ContainerClicks.shiftClick
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestArmorScore
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestAxeScore
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestCrossbowScore
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestPickaxeScore
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestPowerBowScore
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestPunchBowScore
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestShovelScore
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBestSwordDamage
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getBlockCountInInventory
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.getItemCount
import com.github.epsilon.kernel.gameplay.inventory.NavenInvUtils.hasItem
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.math.RandomRanges
import com.github.epsilon.kernel.time.TimerUtils
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.*

object Stealer : Module("Stealer", Category.PLAYER) {

    private val minDelay by intSetting("Min Delay", 110, 0..1000, 50)
    private val maxDelay by intSetting("Max Delay", 140, 0..1000, 50)
    private val autoClose by boolSetting("Auto Close", true)
    private val closeDelay by intSetting("Close Delay", 100, 0..1000, 1, { autoClose })
    private val pickEnderChest by boolSetting("Ender Chest", false)

    private var lastTickScreen: Screen? = null

    private val timer = TimerUtils()

    init {
        safeEventHandler<PlayerTickEvent.Pre> {
            val currentScreen = mc.gui.screen()
            if (currentScreen is AbstractContainerScreen<*>) {
                val menu = currentScreen.menu
                if (menu is ChestMenu) {
                    if (currentScreen != lastTickScreen) {
                        timer.reset()
                    } else {
                        val chestTitle = currentScreen.title.string
                        val chest = Component.translatable("container.chest").string
                        val largeChest = Component.translatable("container.chestDouble").string
                        val enderChest = Component.translatable("container.enderchest").string
                        if (chestTitle == chest || chestTitle == largeChest || chestTitle == "Chest" || pickEnderChest && chestTitle == enderChest) {
                            val nextDelay = RandomRanges.getRandom(minDelay, maxDelay)
                            if (isChestEmpty(menu) && timer.passedMillise(nextDelay.toLong())) {
                                if (autoClose && timer.passedMillise(closeDelay.toLong())) {
                                    player.closeContainer()
                                    timer.reset()
                                }
                            } else {
                                val slots = (0 until menu.rowCount * 9).toMutableList()
                                slots.shuffle()

                                for (slotId in slots) {
                                    val stack = menu.getSlot(slotId).item
                                    if (isItemUseful(stack) && isBestItemInChest(menu, stack) && timer.passedMillise(
                                            nextDelay.toLong()
                                        )
                                    ) {
                                        shiftClick(menu.containerId, slotId)
                                        timer.reset()
                                        if (nextDelay > 0) {
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            lastTickScreen = currentScreen
        }
    }

    fun isWorking(): Boolean {
        return !timer.hasDelayed(3)
    }

    fun NonNullContext.isItemUseful(stack: ItemStack): Boolean {
        if (stack.isEmpty) {
            return false
        } else if (NavenInvUtils.isGodItem(stack) || NavenInvUtils.isSharpnessAxe(stack)) {
            return true
        } else if (NavenInvUtils.isArmor(stack)) {
            val protection = NavenInvUtils.getProtection(stack)
            val bestArmor = getBestArmorScore(NavenInvUtils.getArmorSlot(stack)!!)
            return !(protection <= bestArmor)
        } else if (NavenInvUtils.isSword(stack)) {
            val damage = NavenInvUtils.getSwordDamage(stack)
            val bestDamage = getBestSwordDamage()
            return !(damage <= bestDamage)
        } else if (NavenInvUtils.isPickaxe(stack)) {
            val score = NavenInvUtils.getToolScore(stack)
            val bestScore = getBestPickaxeScore()
            return !(score <= bestScore)
        } else if (NavenInvUtils.isAxe(stack)) {
            val score = NavenInvUtils.getToolScore(stack)
            val bestScore = getBestAxeScore()
            return !(score <= bestScore)
        } else if (NavenInvUtils.isShovel(stack)) {
            val score = NavenInvUtils.getToolScore(stack)
            val bestScore = getBestShovelScore()
            return !(score <= bestScore)
        } else if (stack.item is CrossbowItem) {
            val score = NavenInvUtils.getCrossbowScore(stack)
            val bestScore = getBestCrossbowScore()
            return !(score <= bestScore)
        } else if (stack.item is BowItem && NavenInvUtils.isPunchBow(stack)) {
            val score = NavenInvUtils.getPunchBowScore(stack)
            val bestScore = getBestPunchBowScore()
            return !(score <= bestScore)
        } else if (stack.item is BowItem && NavenInvUtils.isPowerBow(stack)) {
            val score = NavenInvUtils.getPowerBowScore(stack)
            val bestScore = getBestPowerBowScore()
            return !(score <= bestScore)
        } else if (stack.item == Items.COMPASS) {
            return !hasItem(stack.item)
        } else if (stack.item == Items.WATER_BUCKET && getItemCount(Items.WATER_BUCKET) >= InvManager.waterBucketCount) {
            return false
        } else if (stack.item == Items.LAVA_BUCKET && getItemCount(Items.LAVA_BUCKET) >= InvManager.lavaBucketCount) {
            return false
        } else if (stack.item is BlockItem &&
            NavenInvUtils.isValidStack(stack) &&
            getBlockCountInInventory() + stack.count >= InvManager.maxBlockSize
        ) {
            return false
        } else if (stack.item == Items.ARROW && getItemCount(Items.ARROW) + stack.count >= InvManager.maxArrowSize) {
            return false
        } else if (stack.item is FishingRodItem && getItemCount(Items.FISHING_ROD) >= 1) {
            return false
        } else if (stack.item != Items.SNOWBALL && stack.item != Items.EGG
            || getItemCount(Items.SNOWBALL) + getItemCount(Items.EGG) + stack.count < InvManager.maxProjectileSize
            && InvManager.keepProjectile
        ) {
            return stack.item !is StandingAndWallBlockItem && NavenInvUtils.isCommonItemUseful(stack)
        } else {
            return false
        }
    }

    private fun NonNullContext.isBestItemInChest(menu: ChestMenu, stack: ItemStack): Boolean {
        if (!NavenInvUtils.isGodItem(stack) && !NavenInvUtils.isSharpnessAxe(stack)) {
            for (i in 0 until menu.rowCount * 9) {
                val checkStack = menu.getSlot(i).item
                if (NavenInvUtils.isArmor(stack) && NavenInvUtils.isArmor(checkStack)) {
                    if (NavenInvUtils.getArmorSlot(stack) == NavenInvUtils.getArmorSlot(checkStack)
                        && NavenInvUtils.getProtection(checkStack) > NavenInvUtils.getProtection(stack)
                    ) {
                        return false
                    }
                } else if (NavenInvUtils.isSword(stack) && NavenInvUtils.isSword(checkStack)) {
                    if (NavenInvUtils.getSwordDamage(checkStack) > NavenInvUtils.getSwordDamage(stack)) {
                        return false
                    }
                } else if (NavenInvUtils.isPickaxe(stack) && NavenInvUtils.isPickaxe(checkStack)) {
                    if (NavenInvUtils.getToolScore(checkStack) > NavenInvUtils.getToolScore(stack)) {
                        return false
                    }
                } else if (NavenInvUtils.isAxe(stack) && NavenInvUtils.isAxe(checkStack)) {
                    if (NavenInvUtils.getToolScore(checkStack) > NavenInvUtils.getToolScore(stack)) {
                        return false
                    }
                } else if (NavenInvUtils.isShovel(stack)
                    && NavenInvUtils.isShovel(checkStack)
                    && NavenInvUtils.getToolScore(checkStack) > NavenInvUtils.getToolScore(stack)
                ) {
                    return false
                }
            }

            return true
        } else {
            return true
        }
    }

    private fun NonNullContext.isChestEmpty(menu: ChestMenu): Boolean {
        for (i in 0 until menu.rowCount * 9) {
            val item = menu.getSlot(i).item
            if (!item.isEmpty && isItemUseful(item) && isBestItemInChest(menu, item)) {
                return false
            }
        }
        return true
    }

}
