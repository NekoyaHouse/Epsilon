package com.github.epsilon.utils.player;

import net.minecraft.world.InteractionHand;

public record FindItemResult(int slot, int count, int maxCount) {

    /**
     * 判断查找结果是否包含有效槽位。
     *
     * @return 判断结果
     */
    public boolean found() {
        return slot != -1;
    }

    /**
     * 获取查找结果对应的交互手。
     *
     * @return 获取或计算得到的结果
     */
    public InteractionHand getHand() {
        if (slot == 40) { // offhand
            return InteractionHand.OFF_HAND;
        }
        return InteractionHand.MAIN_HAND;
    }

    /**
     * 判断查找结果是否对应主手。
     *
     * @return 判断结果
     */
    public boolean isMainHand() {
        return getHand() == InteractionHand.MAIN_HAND;
    }

    /**
     * 判断查找结果是否对应副手。
     *
     * @return 判断结果
     */
    public boolean isOffhand() {
        return getHand() == InteractionHand.OFF_HAND;
    }

}
