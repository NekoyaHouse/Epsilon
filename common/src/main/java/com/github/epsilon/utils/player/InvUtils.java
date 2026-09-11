package com.github.epsilon.utils.player;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

import static com.github.epsilon.Constants.mc;

public class InvUtils {

    public static int[] invSlots;
    public static int previousSlot = -1;

    /**
     * 使用谓词检测主手物品。
     *
     * @param predicate 物品匹配谓词
     * @return 判断结果
     */
    public static boolean testInMainHand(Predicate<ItemStack> predicate) {
        return predicate.test(mc.player.getMainHandItem());
    }

    /**
     * 使用谓词检测副手物品。
     *
     * @param predicate 物品匹配谓词
     * @return 判断结果
     */
    public static boolean testInOffHand(Predicate<ItemStack> predicate) {
        return predicate.test(mc.player.getOffhandItem());
    }

    /**
     * 在主手、副手和快捷栏中查找匹配物品。
     *
     * @param items 待匹配的物品集合
     * @return 操作结果
     */
    public static FindItemResult findInHotbar(Item... items) {
        return findInHotbar(itemStack -> {
            for (Item item : items) {
                if (itemStack.getItem() == item) return true;
            }
            return false;
        });
    }

    /**
     * 在主手、副手和快捷栏中查找匹配物品。
     *
     * @param isGood 物品匹配谓词
     * @return 操作结果
     */
    public static FindItemResult findInHotbar(Predicate<ItemStack> isGood) {
        if (testInOffHand(isGood)) {
            return new FindItemResult(40, mc.player.getOffhandItem().getCount(), mc.player.getOffhandItem().getMaxStackSize());
        } else if (testInMainHand(isGood)) {
            return new FindItemResult(mc.player.getInventory().getSelectedSlot(), mc.player.getMainHandItem().getCount(), mc.player.getMainHandItem().getMaxStackSize());
        }

        return find(isGood, 0, 8);
    }

    /**
     * 在背包范围内查找匹配物品并汇总数量。
     *
     * @param items 待匹配的物品集合
     * @return 操作结果
     */
    public static FindItemResult find(Item... items) {
        return find(itemStack -> {
            for (Item item : items) {
                if (itemStack.getItem() == item) return true;
            }
            return false;
        });
    }

    /**
     * 在背包范围内查找匹配物品并汇总数量。
     *
     * @param isGood 物品匹配谓词
     * @return 操作结果
     */
    public static FindItemResult find(Predicate<ItemStack> isGood) {
        return find(isGood, 0, mc.player.getInventory().getContainerSize());
    }

    /**
     * 在背包范围内查找匹配物品并汇总数量。
     *
     * @param isGood 物品匹配谓词
     * @param start  搜索起始槽位（含）
     * @param end    搜索结束槽位（含）
     * @return 操作结果
     */
    public static FindItemResult find(Predicate<ItemStack> isGood, int start, int end) {
        int slot = -1, count = 0, maxCount = 0;

        for (int i = start; i <= end; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);

            if (isGood.test(stack)) {
                if (slot == -1) slot = i;
                count += stack.getCount();
                maxCount += stack.getMaxStackSize();
            }
        }

        return new FindItemResult(slot, count, maxCount);
    }

    /**
     * 切换玩家当前选中的快捷栏槽位。
     *
     * @param slot     背包、容器或装备槽位
     * @param saveSwap 是否保存切换前的槽位以便恢复
     */
    public static void swap(int slot, boolean saveSwap) {
        if (slot == 40 || mc.player.getInventory().getSelectedSlot() == slot) {
            return;
        }

        if (saveSwap && previousSlot == -1) {
            previousSlot = mc.player.getInventory().getSelectedSlot();
        } else if (!saveSwap) {
            previousSlot = -1;
        }

        mc.player.getInventory().setSelectedSlot(slot);
    }

    /**
     * 恢复上次保存的快捷栏槽位。
     */
    public static void swapBack() {
        if (previousSlot == -1) return;
        swap(previousSlot, false);
        previousSlot = -1;
    }

    /**
     * 通过容器操作将背包槽位物品临时换到当前快捷栏槽位。
     *
     * @param slot 背包、容器或装备槽位
     */
    public static void invSwap(int slot) {
        int containerSlot = slot;
        if (slot < 9) containerSlot += 36;
        else if (slot == 40) containerSlot = 45;

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        ClickSlotUtils.swap(mc.player.containerMenu.containerId, containerSlot, selectedSlot);
        invSlots = new int[]{containerSlot, selectedSlot};
    }

    /**
     * 撤销上次容器槽位交换。
     */
    public static void invSwapBack() {
        if (invSlots == null || invSlots.length < 2) return;
        ClickSlotUtils.swap(mc.player.containerMenu.containerId, invSlots[0], invSlots[1]);
    }

}
