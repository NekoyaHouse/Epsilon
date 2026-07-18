package com.github.epsilon.utils.player;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Set;

public class EnchantmentUtils {

    public static void getEnchantments(ItemStack itemStack, Object2IntMap<Holder<Enchantment>> enchantments) {
        enchantments.clear();

        if (!itemStack.isEmpty()) {
            Set<Object2IntMap.Entry<Holder<Enchantment>>> itemEnchantments = itemStack.is(Items.ENCHANTED_BOOK)
                    ? itemStack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()
                    : itemStack.getEnchantments().entrySet();

            for (Object2IntMap.Entry<Holder<Enchantment>> entry : itemEnchantments) {
                enchantments.put(entry.getKey(), entry.getIntValue());
            }
        }
    }

    public static int getEnchantmentLevel(ItemStack itemStack, ResourceKey<Enchantment> enchantment) {
        if (itemStack.isEmpty()) return 0;
        Object2IntMap<Holder<Enchantment>> itemEnchantments = new Object2IntArrayMap<>();
        getEnchantments(itemStack, itemEnchantments);
        return getEnchantmentLevel(itemEnchantments, enchantment);
    }

    public static int getEnchantmentLevel(Object2IntMap<Holder<Enchantment>> itemEnchantments, ResourceKey<Enchantment> enchantment) {
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : Object2IntMaps.fastIterable(itemEnchantments)) {
            if (entry.getKey().is(enchantment)) return entry.getIntValue();
        }
        return 0;
    }

    @SafeVarargs
    public static boolean hasEnchantments(ItemStack itemStack, ResourceKey<Enchantment>... enchantments) {
        if (itemStack.isEmpty()) return false;
        Object2IntMap<Holder<Enchantment>> itemEnchantments = new Object2IntArrayMap<>();
        getEnchantments(itemStack, itemEnchantments);

        for (ResourceKey<Enchantment> enchantment : enchantments) {
            if (!hasEnchantment(itemEnchantments, enchantment)) return false;
        }
        return true;
    }

    public static boolean hasEnchantment(ItemStack itemStack, ResourceKey<Enchantment> enchantmentKey) {
        if (itemStack.isEmpty()) return false;
        Object2IntMap<Holder<Enchantment>> itemEnchantments = new Object2IntArrayMap<>();
        getEnchantments(itemStack, itemEnchantments);
        return hasEnchantment(itemEnchantments, enchantmentKey);
    }

    private static boolean hasEnchantment(Object2IntMap<Holder<Enchantment>> itemEnchantments, ResourceKey<Enchantment> enchantmentKey) {
        for (Holder<Enchantment> enchantment : itemEnchantments.keySet()) {
            if (enchantment.is(enchantmentKey)) return true;
        }
        return false;
    }

}
