package com.github.epsilon.utils.player;

import net.minecraft.world.InteractionHand;

public record FindItemResult(int slot, int count, int maxCount) {

    public boolean found() {
        return slot != -1;
    }

    public InteractionHand getHand() {
        if (slot == 40) { // offhand
            return InteractionHand.OFF_HAND;
        }
        return InteractionHand.MAIN_HAND;
    }

    public boolean isMainHand() {
        return getHand() == InteractionHand.MAIN_HAND;
    }

    public boolean isOffhand() {
        return getHand() == InteractionHand.OFF_HAND;
    }

}
