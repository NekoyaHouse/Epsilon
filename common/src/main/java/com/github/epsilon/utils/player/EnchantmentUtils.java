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

    /**
     * 读取物品的附魔并写入目标映射。
     *
     * @param itemStack    物品堆
     * @param enchantments 附魔键或用于接收结果的附魔映射
     */
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

    /**
     * 获取指定附魔的等级。
     *
     * @param itemStack   物品堆
     * @param enchantment 附魔注册表键
     * @return 获取或计算得到的结果
     */
    public static int getEnchantmentLevel(ItemStack itemStack, ResourceKey<Enchantment> enchantment) {
        if (itemStack.isEmpty()) return 0;
        Object2IntMap<Holder<Enchantment>> itemEnchantments = new Object2IntArrayMap<>();
        getEnchantments(itemStack, itemEnchantments);
        return getEnchantmentLevel(itemEnchantments, enchantment);
    }

    /**
     * 获取指定附魔的等级。
     *
     * @param itemEnchantments 已读取的附魔映射
     * @param enchantment      附魔注册表键
     * @return 获取或计算得到的结果
     */
    public static int getEnchantmentLevel(Object2IntMap<Holder<Enchantment>> itemEnchantments, ResourceKey<Enchantment> enchantment) {
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : Object2IntMaps.fastIterable(itemEnchantments)) {
            if (entry.getKey().is(enchantment)) return entry.getIntValue();
        }
        return 0;
    }

    /**
     * 判断物品是否同时具有全部指定附魔。
     *
     * @param itemStack    物品堆
     * @param enchantments 附魔键或用于接收结果的附魔映射
     * @return 判断结果
     */
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

    /**
     * 判断物品是否具有指定附魔。
     *
     * @param itemStack      物品堆
     * @param enchantmentKey 附魔注册表键
     * @return 判断结果
     */
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
