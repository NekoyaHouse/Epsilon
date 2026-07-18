package com.github.epsilon.utils.player;

import net.minecraft.world.inventory.ContainerInput;

import static com.github.epsilon.Constants.mc;

public class ClickSlotUtils {

    public static void clickSlot(int containerId, int slot, int button, ContainerInput action) {
        mc.gameMode.handleContainerInput(containerId, slot, button, action, mc.player);
    }

    public static void clickSlot(int slot, int button, ContainerInput action) {
        clickSlot(mc.player.inventoryMenu.containerId, slot, button, action);
    }

    public static void click(int containerId, int slot) {
        clickSlot(containerId, slot, 0, ContainerInput.PICKUP);
    }

    public static void click(int slot) {
        clickSlot(slot, 0, ContainerInput.PICKUP);
    }

    public static void shiftClick(int containerId, int slot) {
        clickSlot(containerId, slot, 0, ContainerInput.QUICK_MOVE);
    }

    public static void shiftClick(int slot) {
        clickSlot(slot, 0, ContainerInput.QUICK_MOVE);
    }

    public static void drop(int containerId, int slot) {
        clickSlot(containerId, slot, 0, ContainerInput.THROW);
    }

    public static void drop(int slot) {
        clickSlot(slot, 0, ContainerInput.THROW);
    }

    public static void dropAll(int containerId, int slot) {
        clickSlot(containerId, slot, 1, ContainerInput.THROW);
    }

    public static void dropAll(int slot) {
        clickSlot(slot, 1, ContainerInput.THROW);
    }

    public static void swap(int containerId, int slot, int hotbarSlot) {
        clickSlot(containerId, slot, hotbarSlot, ContainerInput.SWAP);
    }

    public static void swap(int slot, int hotbarSlot) {
        clickSlot(slot, hotbarSlot, ContainerInput.SWAP);
    }

}
