package com.github.epsilon.events.impl;

import net.minecraft.core.BlockPos;

public class DestroyedBlockEvent {

    private final BlockPos blockPos;

    public DestroyedBlockEvent(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

}
