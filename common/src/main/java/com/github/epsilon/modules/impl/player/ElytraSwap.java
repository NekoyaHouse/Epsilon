package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.player.ClickSlotUtils;
import com.github.epsilon.utils.player.InvUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

public class ElytraSwap extends Module {

    public static final ElytraSwap INSTANCE = new ElytraSwap();

    private ElytraSwap() {
        super("Elytra Swap", Category.PLAYER);
    }

    private final KeybindSetting activateKey = keybindSetting("Activate Key", GLFW.GLFW_KEY_G);
    private final IntSetting swapDelay = intSetting("Delay", 0, 0, 20, 1);
    private final BoolSetting switchBack = boolSetting("Switch Back", true);
    private final IntSetting switchDelay = intSetting("Switch Delay", 0, 0, 20, 1);
    private final BoolSetting moveToSlot = boolSetting("Move to slot", true);
    private final IntSetting elytraSlotSetting = intSetting("Elytra Slot", 9, 1, 9, 1);

    private boolean isSwinging;
    private boolean isItemSwapped;
    private int swapCounter;
    private int switchCounter;
    private int originalSlot;
    private boolean isKeyDown;

    @Override
    protected void onEnable() {
        resetState();
        isKeyDown = false;
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (mc.screen != null) return;

        boolean pressed = KeybindUtils.isPressed(activateKey.getValue());
        if (!pressed) {
            isKeyDown = false;
        }

        if (pressed) {
            if (isKeyDown && this.originalSlot == -1) return;
            isKeyDown = true;

            if (this.originalSlot == -1) {
                this.originalSlot = mc.player.getInventory().getSelectedSlot();
            }

            if (this.swapCounter < this.swapDelay.getValue()) {
                ++this.swapCounter;
                return;
            }


            boolean wearingElytra = mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);


            Predicate<ItemStack> predicate = wearingElytra ?
                    stack -> {
                        if (stack.isEmpty()) return false;
                        var equippable = stack.get(DataComponents.EQUIPPABLE);
                        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
                    } :
                    stack -> stack.is(Items.ELYTRA);
            if (!this.isItemSwapped) {
                int targetSlot = InvUtils.findInHotbar(predicate).slot();

                if (targetSlot == -1) {
                    if (!this.moveToSlot.getValue()) {
                        this.resetState();
                        return;
                    }


                    int invSlot = InvUtils.find(predicate).slot();
                    if (invSlot != -1) {

                        int containerSlot = invSlot;
                        if (invSlot < 9) containerSlot += 36;

                        int targetHotbarSlot = elytraSlotSetting.getValue() - 1;

                        ClickSlotUtils.swap(mc.player.containerMenu.containerId, containerSlot, targetHotbarSlot);
                        this.swapCounter = 0;
                        return;
                    } else {
                        this.resetState();
                        return;
                    }
                }

                InvUtils.swap(targetSlot, false);
                this.isItemSwapped = true;
            }

            if (!this.isSwinging) {

                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                mc.player.swing(InteractionHand.MAIN_HAND);
                this.isSwinging = true;
            }

            if (this.switchBack.getValue()) {
                this.handleSwitchBack();
            } else {
                this.resetState();
            }
        }
    }

    private void handleSwitchBack() {
        if (this.switchCounter < this.switchDelay.getValue()) {
            ++this.switchCounter;
            return;
        }

        if (this.originalSlot != -1) {
            InvUtils.swap(this.originalSlot, false);
        }
        this.resetState();
    }

    private void resetState() {
        this.originalSlot = -1;
        this.switchCounter = 0;
        this.swapCounter = 0;
        this.isSwinging = false;
        this.isItemSwapped = false;
    }

}
