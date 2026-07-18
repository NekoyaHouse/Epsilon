package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.ClickSlotUtils;
import com.github.epsilon.utils.player.EnchantmentUtils;
import com.github.epsilon.utils.player.InvUtils;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;

public class AutoArmor extends Module {

    public static final AutoArmor INSTANCE = new AutoArmor();

    private AutoArmor() {
        super("Auto Armor", Category.PLAYER);
    }

    private enum ElytraPriority {
        None,
        Always,
        ElytraPlus,
        Ignore
    }

    private enum EnchantPriority {
        Blast,
        Protection
    }

    private final EnumSetting<EnchantPriority> head = enumSetting("Head", EnchantPriority.Protection);
    private final EnumSetting<EnchantPriority> body = enumSetting("Body", EnchantPriority.Protection);
    private final EnumSetting<EnchantPriority> tights = enumSetting("Tights", EnchantPriority.Protection);
    private final EnumSetting<EnchantPriority> feet = enumSetting("Feet", EnchantPriority.Protection);
    private final EnumSetting<ElytraPriority> elytraPriority = enumSetting("Elytra Priority", ElytraPriority.Ignore);
    private final IntSetting delay = intSetting("Delay", 5, 0, 10, 1);
    private final BoolSetting oldVersion = boolSetting("Old Version", false);
    private final BoolSetting pauseInventory = boolSetting("Pause Inventory", false);
    private final BoolSetting noMove = boolSetting("No Move", false);
    private final BoolSetting ignoreCurse = boolSetting("Ignore Curse", true);
    private final BoolSetting strict = boolSetting("Strict", false);

    private int tickDelay;

    private final List<ArmorData> armorList = List.of(
            new ArmorData(EquipmentSlot.FEET, 36),
            new ArmorData(EquipmentSlot.LEGS, 37),
            new ArmorData(EquipmentSlot.CHEST, 38),
            new ArmorData(EquipmentSlot.HEAD, 39)
    );

    @EventHandler
    private void onClientTick(ClientTickEvent.Pre event) {
        if (nullCheck()) return;

        if (
                mc.screen != null
                        && pauseInventory.getValue()
                        && !(mc.screen instanceof ChatScreen)
                        && !(mc.screen instanceof PanelScreen)
                        && !(mc.screen instanceof DropdownScreen)
                        && !(mc.screen instanceof HudEditorScreen)
        ) {
            return;
        }

        if (tickDelay-- > 0) return;

        armorList.forEach(ArmorData::reset);

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            int protection = getProtection(stack);
            if (protection <= 0) continue;

            EquipmentSlot equipmentSlot = getEquipmentSlot(stack);
            if (equipmentSlot == null) continue;

            for (ArmorData armorData : armorList) {
                if (armorData.getEquipmentSlot() == equipmentSlot
                        && protection > armorData.getPrevProtection()
                        && protection > armorData.getNewProtection()) {
                    armorData.setNewSlot(slot);
                    armorData.setNewProtection(protection);
                }
            }
        }

        for (ArmorData armorPiece : armorList) {
            int slot = armorPiece.getNewSlot();
            if (slot == -1) continue;

            if ((armorPiece.getPrevProtection() == -1 || !oldVersion.getValue()) && slot < 9) {
                InvUtils.swap(slot, true);
                try {
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                } finally {
                    InvUtils.swapBack();
                }
            } else {
                if (mc.player.isMoving() && noMove.getValue()) return;

                int inventorySlot = slot < 9 ? 36 + slot : slot;
                int armorSlot = 8 - armorPiece.getEquipmentSlot().getIndex();

                if (strict.getValue()) {
                    mc.getConnection().send(new ServerboundPlayerCommandPacket(
                            mc.player,
                            ServerboundPlayerCommandPacket.Action.STOP_SPRINTING
                    ));
                }

                ClickSlotUtils.click(inventorySlot);
                ClickSlotUtils.click(armorSlot);
                if (armorPiece.getPrevProtection() != -1) {
                    ClickSlotUtils.click(inventorySlot);
                }

                mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
            }

            tickDelay = delay.getValue();
            return;
        }
    }

    private EquipmentSlot getEquipmentSlot(ItemStack stack) {
        if (stack.isEmpty()) return null;

        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
            return null;
        }

        return equippable.slot();
    }

    private int getProtection(ItemStack stack) {
        if (stack.isEmpty()) return -1;

        EquipmentSlot slot = getEquipmentSlot(stack);
        if (slot == null) return 0;

        boolean elytra = stack.is(Items.ELYTRA);
        int enchantmentScore = 0;

        if (elytra) {
            if (!LivingEntity.canGlideUsing(stack, slot)) return 0;

            boolean elytraFlyActive = elytraPriority.is(ElytraPriority.ElytraPlus)
                    && ElytraFly.INSTANCE.isEnabled()
                    && !ElytraFly.INSTANCE.isArmorMode();
            boolean preserveEquippedElytra = elytraPriority.is(ElytraPriority.Ignore)
                    && mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);

            if (elytraFlyActive || preserveEquippedElytra || elytraPriority.is(ElytraPriority.Always)) {
                enchantmentScore = 999;
            }
        }

        int blastMultiplier = 1;
        int protectionMultiplier = 1;

        switch (slot) {
            case HEAD -> {
                if (head.is(EnchantPriority.Protection)) protectionMultiplier = 2;
                else blastMultiplier = 2;
            }
            case CHEST -> {
                if (body.is(EnchantPriority.Protection)) protectionMultiplier = 2;
                else blastMultiplier = 2;
            }
            case LEGS -> {
                if (tights.is(EnchantPriority.Protection)) protectionMultiplier = 2;
                else blastMultiplier = 2;
            }
            case FEET -> {
                if (feet.is(EnchantPriority.Protection)) protectionMultiplier = 2;
                else blastMultiplier = 2;
            }
            default -> {
                return 0;
            }
        }

        enchantmentScore += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PROTECTION) * protectionMultiplier;
        enchantmentScore += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION) * blastMultiplier;

        if (ignoreCurse.getValue() && EnchantmentUtils.hasEnchantment(stack, Enchantments.BINDING_CURSE)) {
            return -999;
        }

        double[] armor = {0.0};
        double[] toughness = {0.0};
        stack.forEachModifier(slot, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ARMOR)) {
                armor[0] += modifier.amount();
            } else if (attribute.equals(Attributes.ARMOR_TOUGHNESS)) {
                toughness[0] += modifier.amount();
            }
        });

        if (!elytra && armor[0] <= 0.0 && toughness[0] <= 0.0) return 0;

        return (int) ((armor[0] + Math.ceil(toughness[0])) * 10.0) + enchantmentScore;
    }

    private final class ArmorData {
        private final EquipmentSlot equipmentSlot;
        private final int inventorySlot;
        private int prevProtection;
        private int newSlot;
        private int newProtection;

        private ArmorData(EquipmentSlot equipmentSlot, int inventorySlot) {
            this.equipmentSlot = equipmentSlot;
            this.inventorySlot = inventorySlot;
            reset();
        }

        private EquipmentSlot getEquipmentSlot() {
            return equipmentSlot;
        }

        private int getPrevProtection() {
            return prevProtection;
        }

        private int getNewSlot() {
            return newSlot;
        }

        private void setNewSlot(int newSlot) {
            this.newSlot = newSlot;
        }

        private int getNewProtection() {
            return newProtection;
        }

        private void setNewProtection(int newProtection) {
            this.newProtection = newProtection;
        }

        private void reset() {
            prevProtection = nullCheck() ? -1 : getProtection(mc.player.getInventory().getItem(inventorySlot));
            newSlot = -1;
            newProtection = -1;
        }
    }

}
