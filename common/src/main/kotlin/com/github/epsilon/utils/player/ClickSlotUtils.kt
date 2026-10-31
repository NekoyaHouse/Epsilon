package com.github.epsilon.utils.player

import com.github.epsilon.kernel.jvm.NonNullContext
import net.minecraft.world.inventory.ContainerInput

object ClickSlotUtils {

    /**
     * 向容器发送指定类型的槽位操作。
     *
     * @param containerId 容器同步编号
     * @param slot        背包、容器或装备槽位
     * @param button      点击按钮编号
     * @param action      容器操作类型
     */
    context(nn: NonNullContext)
    fun clickSlot(containerId: Int, slot: Int, button: Int, action: ContainerInput) {
        nn.gameMode.handleContainerInput(containerId, slot, button, action, nn.player)
    }

    /**
     * 向容器发送指定类型的槽位操作。
     *
     * @param slot   背包、容器或装备槽位
     * @param button 点击按钮编号
     * @param action 容器操作类型
     */
    context(nn: NonNullContext)
    fun clickSlot(slot: Int, button: Int, action: ContainerInput) {
        clickSlot(nn.player.inventoryMenu.containerId, slot, button, action)
    }

    /**
     * 对容器槽位执行普通点击。
     */
    context(nn: NonNullContext)
    fun click(containerId: Int, slot: Int) {
        clickSlot(containerId, slot, 0, ContainerInput.PICKUP)
    }

    /**
     * 对容器槽位执行普通点击。
     */
    context(nn: NonNullContext)
    fun click(slot: Int) {
        clickSlot(slot, 0, ContainerInput.PICKUP)
    }

    /**
     * 对容器槽位执行快速移动点击。
     */
    context(nn: NonNullContext)
    fun shiftClick(containerId: Int, slot: Int) {
        clickSlot(containerId, slot, 0, ContainerInput.QUICK_MOVE)
    }

    /**
     * 对容器槽位执行快速移动点击。
     */
    context(nn: NonNullContext)
    fun shiftClick(slot: Int) {
        clickSlot(slot, 0, ContainerInput.QUICK_MOVE)
    }

    /**
     * 从容器槽位丢出一个物品。
     */
    context(nn: NonNullContext)
    fun drop(containerId: Int, slot: Int) {
        clickSlot(containerId, slot, 0, ContainerInput.THROW)
    }

    /**
     * 从容器槽位丢出一个物品。
     */
    context(nn: NonNullContext)
    fun drop(slot: Int) {
        clickSlot(slot, 0, ContainerInput.THROW)
    }

    /**
     * 丢出容器槽位中的整组物品。
     */
    context(nn: NonNullContext)
    fun dropAll(containerId: Int, slot: Int) {
        clickSlot(containerId, slot, 1, ContainerInput.THROW)
    }

    /**
     * 丢出容器槽位中的整组物品。
     */
    context(nn: NonNullContext)
    fun dropAll(slot: Int) {
        clickSlot(slot, 1, ContainerInput.THROW)
    }

    /**
     * 将容器槽位与快捷栏槽位交换。
     */
    context(nn: NonNullContext)
    fun swap(containerId: Int, slot: Int, hotbarSlot: Int) {
        clickSlot(containerId, slot, hotbarSlot, ContainerInput.SWAP)
    }

    /**
     * 将容器槽位与快捷栏槽位交换。
     */
    context(nn: NonNullContext)
    fun swap(slot: Int, hotbarSlot: Int) {
        clickSlot(slot, hotbarSlot, ContainerInput.SWAP)
    }

}
