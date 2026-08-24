package me.sofurry.via.features.item.negative_item_count;

import com.viaversion.viaversion.protocols.v1_10to1_11.Protocol1_10To1_11;
import me.sofurry.via.util.ItemUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class NegativeItemUtil {

    /**
     * Returns the actual amount of items in the stack, versions older or equal to 1.10 can have negative stack sizes
     * which are not represented by {@link ItemStack#getCount()}.
     *
     * @param stack The stack to get the count from
     * @return The actual amount of items in the stack
     */
    public static int getCount(final ItemStack stack) {
        final CompoundTag tag = ItemUtil.getTagOrNull(stack);
        if (tag != null) {
            return tag.getIntOr(ItemUtil.vvNbtName(Protocol1_10To1_11.class), stack.getCount());
        } else {
            return stack.getCount();
        }
    }

}
