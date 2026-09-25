package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.orchestration.*;
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
        setDispatchMode(ModuleDispatchMode.MANAGED);
        part(new ObservePart());
        part(new CommitPart());
    }

    /**
     * OBSERVE：只在背包界面里解析悬停格与目标槽位，并推进点击间隔倒计时。
     * <p>
     * 切换快捷栏槽位与容器点击都是副作用，这里一个都不做。
     */
    private final class ObservePart implements ModulePart {
        @Override
        public void declare(ModuleDeclaration declaration) {
            node(PlayerTickEvent.Pre.class, NodeKey.of("observe.hover_slot"))
                    .phase(Phase.OBSERVE)
                    .handler(HoverTotem.this::observeHoverSlot);
        }
    }

    /**
     * COMMIT：切换选中槽位与交换容器槽位都是外部副作用。
     */
    private final class CommitPart implements ModulePart {
        @Override
        public void declare(ModuleDeclaration declaration) {
            node(PlayerTickEvent.Pre.class, NodeKey.of("commit.swap_totem"))
                    .phase(Phase.COMMIT)
                    .handler(HoverTotem.this::commitSwapTotem);
        }
    }

    private final DoubleSetting delay = doubleSetting("Delay", 0.0, 0.0, 20.0, 0.1);
    private final DoubleSetting randomDelay = doubleSetting("Random Delay", 0.0, 0.0, 10.0, 0.1);
    private final BoolSetting hotbar = boolSetting("Hotbar", true);
    private final DoubleSetting slot = doubleSetting("Totem Slot", 1.0, 1.0, 9.0, 1.0);
    private final BoolSetting autoSwitch = boolSetting("Auto Switch", true);

    private int clock;
    private final Random random = new Random();
    private int currentDelay;
    /** observe.hover_slot 产出的槽位事实：-1 表示本 tick 不执行该动作。 */
    private int pendingAutoSwitchSlot = -1;
    private int pendingSwapSlotIndex = -1;
    private int pendingSwapTarget = -1;

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
    private void observeHoverSlot(PlayerTickEvent.Pre event) {
        this.pendingAutoSwitchSlot = -1;
        this.pendingSwapSlotIndex = -1;
        this.pendingSwapTarget = -1;

        if (mc.gui.screen() instanceof InventoryScreen inv) {
            Slot hoveredSlot = inv.hoveredSlot;

            if (this.autoSwitch.getValue()) {
                int slotValue = this.slot.getValue().intValue();
                this.pendingAutoSwitchSlot = slotValue - 1;
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
                        this.pendingSwapSlotIndex = slotIndex;
                        this.pendingSwapTarget = totem;
                    } else if (!mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                        if (this.clock > 0) {
                            --this.clock;
                            return;
                        }
                        this.pendingSwapSlotIndex = slotIndex;
                        this.pendingSwapTarget = 40;
                    }
                }
            }
        } else {
            this.currentDelay = this.getRandomDelay();
            this.clock = this.currentDelay;
        }
    }

    private void commitSwapTotem(PlayerTickEvent.Pre event) {
        if (this.pendingAutoSwitchSlot >= 0) {
            mc.player.getInventory().setSelectedSlot(this.pendingAutoSwitchSlot);
        }

        if (this.pendingSwapTarget < 0) {
            return;
        }

        ClickSlotUtils.swap(mc.player.containerMenu.containerId, this.pendingSwapSlotIndex, this.pendingSwapTarget);
        this.currentDelay = this.getRandomDelay();
        this.clock = this.currentDelay;
        this.pendingSwapSlotIndex = -1;
        this.pendingSwapTarget = -1;
    }

}
