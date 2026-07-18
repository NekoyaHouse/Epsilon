package com.github.epsilon.events.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class BlockCollisionEvent {

    private BlockState blockState;
    private final BlockPos blockPos;

    public BlockCollisionEvent(BlockState blockState, BlockPos blockPos) {
        this.blockState = blockState;
        this.blockPos = blockPos;
    }

    public BlockState getState() {
        return blockState;
    }

    public BlockPos getPos() {
        return blockPos;
    }

    public void setState(BlockState blockState) {
        this.blockState = blockState;
    }

}
