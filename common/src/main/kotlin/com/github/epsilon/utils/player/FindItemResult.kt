package com.github.epsilon.utils.player

import net.minecraft.world.InteractionHand

/**
 * 物品查找结果。
 *
 * 由原 Java record 迁移为 data class；槽位、数量与最大堆叠数保持原字段语义。
 */
data class FindItemResult(val slot: Int, val count: Int, val maxCount: Int) {

    /**
     * 判断查找结果是否包含有效槽位。
     */
    fun found(): Boolean {
        return slot != -1
    }

    /**
     * 获取查找结果对应的交互手。
     */
    fun getHand(): InteractionHand {
        if (slot == 40) { // offhand
            return InteractionHand.OFF_HAND
        }
        return InteractionHand.MAIN_HAND
    }

    /**
     * 判断查找结果是否对应主手。
     */
    fun isMainHand(): Boolean {
        return getHand() == InteractionHand.MAIN_HAND
    }

    /**
     * 判断查找结果是否对应副手。
     */
    fun isOffhand(): Boolean {
        return getHand() == InteractionHand.OFF_HAND
    }

}
