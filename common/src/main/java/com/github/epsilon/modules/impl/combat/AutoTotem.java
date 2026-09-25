package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.orchestration.*;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.player.ClickSlotUtils;
import com.github.epsilon.utils.player.InvHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class AutoTotem extends Module {

    public static final AutoTotem INSTANCE = new AutoTotem();

    private final BoolSetting strict = boolSetting("Strict", true);
    private final DoubleSetting health = doubleSetting("Health", 16.0, 0.0, 36.0, 0.5);
    private final BoolSetting checkGapple = boolSetting("Check Gapple", true);

    /** observe.totem_need 产出的源槽位，-1 表示本 tick 无需移动。 */
    private int pendingSlot = -1;

    private AutoTotem() {
        super("Auto Totem", Category.COMBAT);
        setDispatchMode(ModuleDispatchMode.MANAGED);
        part(new ObservePart());
        part(new CommitPart());
    }

    /**
     * OBSERVE：只判断是否需要图腾、以及图腾在哪个槽位，不做任何容器点击。
     */
    private final class ObservePart implements ModulePart {
        @Override
        public void declare(ModuleDeclaration declaration) {
            node(PlayerTickEvent.Pre.class, NodeKey.of("observe.totem_need"))
                    .phase(Phase.OBSERVE)
                    .handler(AutoTotem.this::observeTotemNeed);
        }
    }

    /**
     * COMMIT：把图腾换到副手需要点击容器槽位，属于外部副作用。
     */
    private final class CommitPart implements ModulePart {
        @Override
        public void declare(ModuleDeclaration declaration) {
            node(PlayerTickEvent.Pre.class, NodeKey.of("commit.move_totem"))
                    .phase(Phase.COMMIT)
                    .handler(AutoTotem.this::commitMoveTotem);
        }
    }

    @Override
    public String getInfo() {
        if (nullCheck()) return null;
        return String.valueOf(InvHelper.getItemCount(Items.TOTEM_OF_UNDYING));
    }
    private void observeTotemNeed(PlayerTickEvent.Pre event) {
        pendingSlot = -1;

        if (nullCheck() || mc.gameMode == null) return;

        if (!shouldHoldTotem()) {
            return;
        }

        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return;
        }

        int slot = InvHelper.getItemSlot(Items.TOTEM_OF_UNDYING);
        if (slot == -1) {
            return;
        }

        pendingSlot = slot;
    }

    private void commitMoveTotem(PlayerTickEvent.Pre event) {
        int slot = pendingSlot;
        pendingSlot = -1;
        if (slot == -1) {
            return;
        }

        moveItemToOffhand(slot);
    }

    private boolean shouldHoldTotem() {
        float totalHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        if (totalHealth <= health.getValue().floatValue()) {
            return true;
        }

        // Keep an empty offhand safe by filling it with a totem.
        if (mc.player.getOffhandItem().isEmpty()) {
            return true;
        }

        // Simple void safety check for modern overworld min Y.
        if (mc.player.getY() < -64.0) {
            return true;
        }

        if (checkGapple.getValue()) {
            Item mainHandItem = mc.player.getMainHandItem().getItem();
            if (mainHandItem == Items.GOLDEN_APPLE || mainHandItem == Items.ENCHANTED_GOLDEN_APPLE) {
                return true;
            }
        }

        return false;
    }

    private void moveItemToOffhand(int slot) {
        if (slot < 9) {
            slot += 36;
        }

        if (!strict.getValue()) {
            ClickSlotUtils.swap(slot, 40);
            return;
        }

        ClickSlotUtils.click(slot);
        ClickSlotUtils.click(45);

        if (!mc.player.inventoryMenu.getCarried().isEmpty()) {
            ClickSlotUtils.click(slot);
        }
    }

}
