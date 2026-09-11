package com.github.epsilon.events.impl;

import com.github.epsilon.events.bus.Cancellable;
import net.minecraft.core.BlockPos;

public class DestroyBlockEvent extends Cancellable {

    private final BlockPos blockPos;

    public DestroyBlockEvent(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

}
