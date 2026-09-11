package com.github.epsilon.utils.player;

import net.minecraft.world.inventory.ContainerInput;

import static com.github.epsilon.Constants.mc;

public class ClickSlotUtils {

    /**
     * 向容器发送指定类型的槽位操作。
     *
     * @param containerId 容器同步编号
     * @param slot        背包、容器或装备槽位
     * @param button      点击按钮编号
     * @param action      容器操作类型
     */
    public static void clickSlot(int containerId, int slot, int button, ContainerInput action) {
        mc.gameMode.handleContainerInput(containerId, slot, button, action, mc.player);
    }

    /**
     * 向容器发送指定类型的槽位操作。
     *
     * @param slot   背包、容器或装备槽位
     * @param button 点击按钮编号
     * @param action 容器操作类型
     */
    public static void clickSlot(int slot, int button, ContainerInput action) {
        clickSlot(mc.player.inventoryMenu.containerId, slot, button, action);
    }

    /**
     * 对容器槽位执行普通点击。
     *
     * @param containerId 容器同步编号
     * @param slot        背包、容器或装备槽位
     */
    public static void click(int containerId, int slot) {
        clickSlot(containerId, slot, 0, ContainerInput.PICKUP);
    }

    /**
     * 对容器槽位执行普通点击。
     *
     * @param slot 背包、容器或装备槽位
     */
    public static void click(int slot) {
        clickSlot(slot, 0, ContainerInput.PICKUP);
    }

    /**
     * 对容器槽位执行快速移动点击。
     *
     * @param containerId 容器同步编号
     * @param slot        背包、容器或装备槽位
     */
    public static void shiftClick(int containerId, int slot) {
        clickSlot(containerId, slot, 0, ContainerInput.QUICK_MOVE);
    }

    /**
     * 对容器槽位执行快速移动点击。
     *
     * @param slot 背包、容器或装备槽位
     */
    public static void shiftClick(int slot) {
        clickSlot(slot, 0, ContainerInput.QUICK_MOVE);
    }

    /**
     * 从容器槽位丢出一个物品。
     *
     * @param containerId 容器同步编号
     * @param slot        背包、容器或装备槽位
     */
    public static void drop(int containerId, int slot) {
        clickSlot(containerId, slot, 0, ContainerInput.THROW);
    }

    /**
     * 从容器槽位丢出一个物品。
     *
     * @param slot 背包、容器或装备槽位
     */
    public static void drop(int slot) {
        clickSlot(slot, 0, ContainerInput.THROW);
    }

    /**
     * 丢出容器槽位中的整组物品。
     *
     * @param containerId 容器同步编号
     * @param slot        背包、容器或装备槽位
     */
    public static void dropAll(int containerId, int slot) {
        clickSlot(containerId, slot, 1, ContainerInput.THROW);
    }

    /**
     * 丢出容器槽位中的整组物品。
     *
     * @param slot 背包、容器或装备槽位
     */
    public static void dropAll(int slot) {
        clickSlot(slot, 1, ContainerInput.THROW);
    }

    /**
     * 将容器槽位与快捷栏槽位交换。
     *
     * @param containerId 容器同步编号
     * @param slot        背包、容器或装备槽位
     * @param hotbarSlot  快捷栏槽位编号
     */
    public static void swap(int containerId, int slot, int hotbarSlot) {
        clickSlot(containerId, slot, hotbarSlot, ContainerInput.SWAP);
    }

    /**
     * 将容器槽位与快捷栏槽位交换。
     *
     * @param slot       背包、容器或装备槽位
     * @param hotbarSlot 快捷栏槽位编号
     */
    public static void swap(int slot, int hotbarSlot) {
        clickSlot(slot, hotbarSlot, ContainerInput.SWAP);
    }

}
