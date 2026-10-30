package com.github.epsilon.modules.impl.player

import com.github.epsilon.utils.*
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.math.MathUtils
import com.github.epsilon.utils.player
import com.github.epsilon.utils.player.ClickSlotUtils
import com.github.epsilon.utils.player.InvHelper
import com.github.epsilon.utils.timer.TimerUtils
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.FishingRodItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.StandingAndWallBlockItem

object Stealer : Module("Stealer", Category.PLAYER) {

    private val minDelay by intSetting("Min Delay", 110, 0, 1000, 50)
    private val maxDelay by intSetting("Max Delay", 140, 0, 1000, 50)
    private val autoClose by boolSetting("Auto Close", true)
    private val closeDelay by intSetting("Close Delay", 100, 0, 1000, 1, Setting.Dependency { autoClose })
    private val pickEnderChest by boolSetting("Ender Chest", false)

    private var lastTickScreen: Screen? = null

    private val timer = TimerUtils()

    init {
        nonNullListener<PlayerTickEvent.Pre> {
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
                            val nextDelay = MathUtils.getRandom(minDelay, maxDelay)
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
                                    if (isItemUseful(stack) && isBestItemInChest(menu, stack) && timer.passedMillise(nextDelay.toLong())) {
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

    fun isItemUseful(stack: ItemStack): Boolean {
        if (stack.isEmpty) {
            return false
        } else if (InvHelper.isGodItem(stack) || InvHelper.isSharpnessAxe(stack)) {
            return true
        } else if (InvHelper.isArmor(stack)) {
            val protection = InvHelper.getProtection(stack)
            val bestArmor = InvHelper.getBestArmorScore(InvHelper.getArmorSlot(stack)!!)
            return !(protection <= bestArmor)
        } else if (InvHelper.isSword(stack)) {
            val damage = InvHelper.getSwordDamage(stack)
            val bestDamage = InvHelper.getBestSwordDamage()
            return !(damage <= bestDamage)
        } else if (InvHelper.isPickaxe(stack)) {
            val score = InvHelper.getToolScore(stack)
            val bestScore = InvHelper.getBestPickaxeScore()
            return !(score <= bestScore)
        } else if (InvHelper.isAxe(stack)) {
            val score = InvHelper.getToolScore(stack)
            val bestScore = InvHelper.getBestAxeScore()
            return !(score <= bestScore)
        } else if (InvHelper.isShovel(stack)) {
            val score = InvHelper.getToolScore(stack)
            val bestScore = InvHelper.getBestShovelScore()
            return !(score <= bestScore)
        } else if (stack.item is CrossbowItem) {
            val score = InvHelper.getCrossbowScore(stack)
            val bestScore = InvHelper.getBestCrossbowScore()
            return !(score <= bestScore)
        } else if (stack.item is BowItem && InvHelper.isPunchBow(stack)) {
            val score = InvHelper.getPunchBowScore(stack)
            val bestScore = InvHelper.getBestPunchBowScore()
            return !(score <= bestScore)
        } else if (stack.item is BowItem && InvHelper.isPowerBow(stack)) {
            val score = InvHelper.getPowerBowScore(stack)
            val bestScore = InvHelper.getBestPowerBowScore()
            return !(score <= bestScore)
        } else if (stack.item == Items.COMPASS) {
            return !InvHelper.hasItem(stack.item)
        } else if (stack.item == Items.WATER_BUCKET && InvHelper.getItemCount(Items.WATER_BUCKET) >= InvManager.waterBucketCount) {
            return false
        } else if (stack.item == Items.LAVA_BUCKET && InvHelper.getItemCount(Items.LAVA_BUCKET) >= InvManager.lavaBucketCount) {
            return false
        } else if (stack.item is BlockItem &&
            InvHelper.isValidStack(stack) &&
            InvHelper.getBlockCountInInventory() + stack.count >= InvManager.maxBlockSize
        ) {
            return false
        } else if (stack.item == Items.ARROW && InvHelper.getItemCount(Items.ARROW) + stack.count >= InvManager.maxArrowSize) {
            return false
        } else if (stack.item is FishingRodItem && InvHelper.getItemCount(Items.FISHING_ROD) >= 1) {
            return false
        } else if (stack.item != Items.SNOWBALL && stack.item != Items.EGG
            || InvHelper.getItemCount(Items.SNOWBALL) + InvHelper.getItemCount(Items.EGG) + stack.count < InvManager.maxProjectileSize
            && InvManager.keepProjectile
        ) {
            return stack.item !is StandingAndWallBlockItem && InvHelper.isCommonItemUseful(stack)
        } else {
            return false
        }
    }

    private fun isBestItemInChest(menu: ChestMenu, stack: ItemStack): Boolean {
        if (!InvHelper.isGodItem(stack) && !InvHelper.isSharpnessAxe(stack)) {
            for (i in 0 until menu.rowCount * 9) {
                val checkStack = menu.getSlot(i).item
                if (InvHelper.isArmor(stack) && InvHelper.isArmor(checkStack)) {
                    if (InvHelper.getArmorSlot(stack) == InvHelper.getArmorSlot(checkStack)
                        && InvHelper.getProtection(checkStack) > InvHelper.getProtection(stack)
                    ) {
                        return false
                    }
                } else if (InvHelper.isSword(stack) && InvHelper.isSword(checkStack)) {
                    if (InvHelper.getSwordDamage(checkStack) > InvHelper.getSwordDamage(stack)) {
                        return false
                    }
                } else if (InvHelper.isPickaxe(stack) && InvHelper.isPickaxe(checkStack)) {
                    if (InvHelper.getToolScore(checkStack) > InvHelper.getToolScore(stack)) {
                        return false
                    }
                } else if (InvHelper.isAxe(stack) && InvHelper.isAxe(checkStack)) {
                    if (InvHelper.getToolScore(checkStack) > InvHelper.getToolScore(stack)) {
                        return false
                    }
                } else if (InvHelper.isShovel(stack)
                    && InvHelper.isShovel(checkStack)
                    && InvHelper.getToolScore(checkStack) > InvHelper.getToolScore(stack)
                ) {
                    return false
                }
            }

            return true
        } else {
            return true
        }
    }

    private fun isChestEmpty(menu: ChestMenu): Boolean {
        for (i in 0 until menu.rowCount * 9) {
            val item = menu.getSlot(i).item
            if (!item.isEmpty && isItemUseful(item) && isBestItemInChest(menu, item)) {
                return false
            }
        }
        return true
    }

}
