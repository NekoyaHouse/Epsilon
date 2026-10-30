package com.github.epsilon.utils.player

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntMaps
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.ItemEnchantments

/**
 * 物品附魔读取工具类。
 *
 * 全部为纯函数，不依赖玩家与世界，因此不需要客户端上下文。
 */
object EnchantmentUtils {

    /**
     * 读取物品的附魔并写入目标映射。
     *
     * @param itemStack    物品堆
     * @param enchantments 用于接收结果的附魔映射
     */
    fun getEnchantments(itemStack: ItemStack, enchantments: Object2IntMap<Holder<Enchantment>>) {
        enchantments.clear()

        if (!itemStack.isEmpty) {
            val itemEnchantments = if (itemStack.`is`(Items.ENCHANTED_BOOK)) {
                itemStack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()
            } else {
                itemStack.enchantments.entrySet()
            }

            for (entry in itemEnchantments) {
                enchantments.put(entry.key, entry.getIntValue())
            }
        }
    }

    /**
     * 获取指定附魔的等级。
     *
     * @param itemStack   物品堆
     * @param enchantment 附魔注册表键
     */
    fun getEnchantmentLevel(itemStack: ItemStack, enchantment: ResourceKey<Enchantment>): Int {
        if (itemStack.isEmpty) return 0
        val itemEnchantments = Object2IntArrayMap<Holder<Enchantment>>()
        getEnchantments(itemStack, itemEnchantments)
        return getEnchantmentLevel(itemEnchantments, enchantment)
    }

    /**
     * 获取指定附魔的等级。
     *
     * @param itemEnchantments 已读取的附魔映射
     * @param enchantment      附魔注册表键
     */
    fun getEnchantmentLevel(itemEnchantments: Object2IntMap<Holder<Enchantment>>, enchantment: ResourceKey<Enchantment>): Int {
        for (entry in Object2IntMaps.fastIterable(itemEnchantments)) {
            if (entry.key.`is`(enchantment)) return entry.getIntValue()
        }
        return 0
    }

    /**
     * 判断物品是否同时具有全部指定附魔。
     *
     * @param itemStack    物品堆
     * @param enchantments 附魔注册表键
     */
    fun hasEnchantments(itemStack: ItemStack, vararg enchantments: ResourceKey<Enchantment>): Boolean {
        if (itemStack.isEmpty) return false
        val itemEnchantments = Object2IntArrayMap<Holder<Enchantment>>()
        getEnchantments(itemStack, itemEnchantments)

        for (enchantment in enchantments) {
            if (!hasEnchantment(itemEnchantments, enchantment)) return false
        }
        return true
    }

    /**
     * 判断物品是否具有指定附魔。
     *
     * @param itemStack      物品堆
     * @param enchantmentKey 附魔注册表键
     */
    fun hasEnchantment(itemStack: ItemStack, enchantmentKey: ResourceKey<Enchantment>): Boolean {
        if (itemStack.isEmpty) return false
        val itemEnchantments = Object2IntArrayMap<Holder<Enchantment>>()
        getEnchantments(itemStack, itemEnchantments)
        return hasEnchantment(itemEnchantments, enchantmentKey)
    }

    private fun hasEnchantment(itemEnchantments: Object2IntMap<Holder<Enchantment>>, enchantmentKey: ResourceKey<Enchantment>): Boolean {
        for (entry in Object2IntMaps.fastIterable(itemEnchantments)) {
            if (entry.key.`is`(enchantmentKey)) return true
        }
        return false
    }

}
