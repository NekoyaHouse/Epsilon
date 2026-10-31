package com.github.epsilon.utils.player

import com.github.epsilon.utils.safety.*
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.function.Predicate

/**
 * 背包快捷栏查找与切换工具类。
 */
object InvUtils {

    var invSlots: IntArray? = null

    var previousSlot: Int = -1

    /**
     * 使用谓词检测主手物品。
     *
     * @param predicate 物品匹配谓词
     */
    context(nn: NonNullContext)
    fun testInMainHand(predicate: Predicate<ItemStack>): Boolean {
        return predicate.test(player.mainHandItem)
    }

    /**
     * 使用谓词检测副手物品。
     *
     * @param predicate 物品匹配谓词
     */
    context(nn: NonNullContext)
    fun testInOffHand(predicate: Predicate<ItemStack>): Boolean {
        return predicate.test(player.offhandItem)
    }

    /**
     * 在主手、副手和快捷栏中查找匹配物品。
     *
     * @param items 待匹配的物品集合
     */
    context(nn: NonNullContext)
    fun findInHotbar(vararg items: Item): FindItemResult {
        return findInHotbar { itemStack ->
            items.any { itemStack.item === it }
        }
    }

    /**
     * 在主手、副手和快捷栏中查找匹配物品。
     *
     * @param isGood 物品匹配谓词
     */
    context(nn: NonNullContext)
    fun findInHotbar(isGood: Predicate<ItemStack>): FindItemResult {
        if (testInOffHand(isGood)) {
            val offhand = player.offhandItem
            return FindItemResult(40, offhand.count, offhand.maxStackSize)
        } else if (testInMainHand(isGood)) {
            val mainHand = player.mainHandItem
            return FindItemResult(player.inventory.selectedSlot, mainHand.count, mainHand.maxStackSize)
        }

        return find(isGood, 0, 8)
    }

    /**
     * 在背包范围内查找匹配物品并汇总数量。
     *
     * @param items 待匹配的物品集合
     */
    context(nn: NonNullContext)
    fun find(vararg items: Item): FindItemResult {
        return find { itemStack ->
            items.any { itemStack.item === it }
        }
    }

    /**
     * 在背包范围内查找匹配物品并汇总数量。
     *
     * @param isGood 物品匹配谓词
     */
    context(nn: NonNullContext)
    fun find(isGood: Predicate<ItemStack>): FindItemResult {
        return find(isGood, 0, player.inventory.containerSize)
    }

    /**
     * 在背包范围内查找匹配物品并汇总数量。
     *
     * @param isGood 物品匹配谓词
     * @param start  搜索起始槽位（含）
     * @param end    搜索结束槽位（含）
     */
    context(nn: NonNullContext)
    fun find(isGood: Predicate<ItemStack>, start: Int, end: Int): FindItemResult {
        var slot = -1
        var count = 0
        var maxCount = 0
        val inventory = player.inventory

        for (i in start..end) {
            val stack = inventory.getItem(i)

            if (isGood.test(stack)) {
                if (slot == -1) slot = i
                count += stack.count
                maxCount += stack.maxStackSize
            }
        }

        return FindItemResult(slot, count, maxCount)
    }

    /**
     * 切换玩家当前选中的快捷栏槽位。
     *
     * @param slot     背包、容器或装备槽位
     * @param saveSwap 是否保存切换前的槽位以便恢复
     */
    context(nn: NonNullContext)
    fun swap(slot: Int, saveSwap: Boolean) {
        val inventory = player.inventory

        if (slot == 40 || inventory.selectedSlot == slot) {
            return
        }

        if (saveSwap && previousSlot == -1) {
            previousSlot = inventory.selectedSlot
        } else if (!saveSwap) {
            previousSlot = -1
        }

        inventory.selectedSlot = slot
    }

    /**
     * 恢复上次保存的快捷栏槽位。
     */
    context(nn: NonNullContext)
    fun swapBack() {
        if (previousSlot == -1) return
        swap(previousSlot, false)
        previousSlot = -1
    }

    /**
     * 通过容器操作将背包槽位物品临时换到当前快捷栏槽位。
     *
     * @param slot 背包、容器或装备槽位
     */
    context(nn: NonNullContext)
    fun invSwap(slot: Int) {
        var containerSlot = slot
        if (slot < 9) containerSlot += 36
        else if (slot == 40) containerSlot = 45

        val selectedSlot = player.inventory.selectedSlot
        ClickSlotUtils.swap(player.containerMenu.containerId, containerSlot, selectedSlot)
        invSlots = intArrayOf(containerSlot, selectedSlot)
    }

    /**
     * 撤销上次容器槽位交换。
     */
    context(nn: NonNullContext)
    fun invSwapBack() {
        val slots = invSlots ?: return
        if (slots.size < 2) return

        ClickSlotUtils.swap(player.containerMenu.containerId, slots[0], slots[1])
    }

}
