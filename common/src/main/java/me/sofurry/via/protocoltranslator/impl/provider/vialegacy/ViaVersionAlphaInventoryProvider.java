package me.sofurry.via.protocoltranslator.impl.provider.vialegacy;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import me.sofurry.via.protocoltranslator.translator.ItemTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.vialegacy.protocol.alpha.a1_2_3_5_1_2_6tob1_0_1_1_1.provider.AlphaInventoryProvider;

import java.util.List;

public final class ViaVersionAlphaInventoryProvider extends AlphaInventoryProvider {

    @Override
    public boolean usesInventoryTracker() {
        return false;
    }

    private Item[] convertItems(final List<ItemStack> stacks) {
        final Item[] items = new Item[stacks.size()];

        for (int i = 0; i < items.length; i++) {
            items[i] = convertItem(stacks.get(i));
        }
        return items;
    }

    private Item convertItem(final ItemStack stack) {
        if (!stack.isEmpty()) {
            final Item item = ItemTranslator.mcToVia(stack, LegacyProtocolVersion.b1_8tob1_8_1);
            if (item != null) {
                return item.copy();
            }
        }
        return null;
    }

    @Override
    public Item[] getMainInventoryItems(UserConnection connection) {
        final Player player = Minecraft.getInstance().player;
        if (player == null) {
            return new Item[37];
        } else {
            return convertItems(player.getInventory().getNonEquipmentItems());
        }
    }

    @Override
    public Item[] getCraftingInventoryItems(UserConnection connection) {
        final Player player = Minecraft.getInstance().player;
        if (player == null) {
            return new Item[4];
        } else {
            return convertItems(player.inventoryMenu.getCraftSlots().getItems());
        }
    }

    @Override
    public Item[] getArmorInventoryItems(UserConnection connection) {
        final Player player = Minecraft.getInstance().player;
        final Item[] items = new Item[4];
        if (player != null) {
            final Inventory inventory = player.getInventory();
            items[0] = convertItem(inventory.equipment.get(EquipmentSlot.FEET));
            items[1] = convertItem(inventory.equipment.get(EquipmentSlot.LEGS));
            items[2] = convertItem(inventory.equipment.get(EquipmentSlot.CHEST));
            items[3] = convertItem(inventory.equipment.get(EquipmentSlot.HEAD));
        }

        return items;
    }

    @Override
    public Item[] getContainerItems(UserConnection connection) {
        final Player player = Minecraft.getInstance().player;
        if (player == null) {
            return new Item[37];
        } else {
            return convertItems(player.containerMenu.getItems());
        }
    }

    @Override
    public void addToInventory(UserConnection connection, Item item) {
        final Player player = Minecraft.getInstance().player;
        player.getInventory().add(ItemTranslator.viaToMc(item, LegacyProtocolVersion.b1_8tob1_8_1));
    }

}
