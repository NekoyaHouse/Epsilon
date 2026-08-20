package me.sofurry.via.util;

import com.viaversion.viaversion.api.protocol.Protocol;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ItemUtil {

    // ViaVersion's 1.20.5 -> 1.20.3 protocol will save the original item nbt inside custom data to later restore
    // it for creative clients, we can use this to get nbt stored in older protocols as well
    public static CompoundTag getTagOrNull(final ItemStack stack) {
        final CustomData tag = stack.get(DataComponents.CUSTOM_DATA);
        if (tag != null) {
            return tag.copyTag();
        } else {
            return null;
        }
    }

    // See ItemRewriter#nbtTagName for the format

    public static String vvNbtName(final Class<? extends Protocol<?, ?, ?, ?>> protocolClass) {
        return "VV|" + protocolClass.getSimpleName();
    }

    public static String vvNbtName(final Class<? extends Protocol<?, ?, ?, ?>> protocolClass, final String name) {
        return "VV|" + protocolClass.getSimpleName() + "|" + name;
    }

}
