package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.player.ClickSlotUtils;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

import java.util.Random;

public class HoverTotem extends Module {

    public static final HoverTotem INSTANCE = new HoverTotem();

    private HoverTotem() {
        super("Hover Totem", Category.COMBAT);
    }

    private final DoubleSetting delay = doubleSetting("Delay", 0.0, 0.0, 20.0, 0.1);
    private final DoubleSetting randomDelay = doubleSetting("Random Delay", 0.0, 0.0, 10.0, 0.1);
    private final BoolSetting hotbar = boolSetting("Hotbar", true);
    private final DoubleSetting slot = doubleSetting("Totem Slot", 1.0, 1.0, 9.0, 1.0);
    private final BoolSetting autoSwitch = boolSetting("Auto Switch", true);

    private int clock;
    private final Random random = new Random();
    private int currentDelay;

    @Override
    protected void onEnable() {
        this.clock = 0;
        this.currentDelay = 0;
    }

    private int getRandomDelay() {
        int baseDelay = delay.getValue().intValue();
        double randomDelayValue = randomDelay.getValue();

        if (randomDelayValue <= 0.0) {
            return baseDelay;
        }

        double randomValue = this.random.nextDouble() * randomDelayValue;
        double totalDelay = baseDelay + randomValue;

        return (int) Math.ceil(totalDelay);
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (mc.screen instanceof InventoryScreen inv) {
            Slot hoveredSlot = inv.hoveredSlot;

            if (this.autoSwitch.getValue()) {
                int slotValue = this.slot.getValue().intValue();
                mc.player.getInventory().setSelectedSlot(slotValue - 1);
            }

            if (hoveredSlot != null) {
                int slotIndex = hoveredSlot.index;
                if (slotIndex > 35) {
                    return;
                }

                int totemSlot = this.slot.getValue().intValue();
                int totem = totemSlot - 1;

                if (hoveredSlot.getItem().is(Items.TOTEM_OF_UNDYING)) {
                    if (this.hotbar.getValue() && !mc.player.getInventory().getItem(totem).is(Items.TOTEM_OF_UNDYING)) {
                        if (this.clock > 0) {
                            --this.clock;
                            return;
                        }
                        ClickSlotUtils.swap(mc.player.containerMenu.containerId, slotIndex, totem);
                        this.currentDelay = this.getRandomDelay();
                        this.clock = this.currentDelay;
                    } else if (!mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                        if (this.clock > 0) {
                            --this.clock;
                            return;
                        }
                        ClickSlotUtils.swap(mc.player.containerMenu.containerId, slotIndex, 40);
                        this.currentDelay = this.getRandomDelay();
                        this.clock = this.currentDelay;
                    }
                }
            }
        } else {
            this.currentDelay = this.getRandomDelay();
            this.clock = this.currentDelay;
        }
    }

}
