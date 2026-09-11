package com.github.epsilon.events.impl;

import com.github.epsilon.events.bus.Cancellable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

public class PlaceBlockEvent extends Cancellable {

    private final BlockPos blockPos;
    private final Block block;

    public PlaceBlockEvent(BlockPos blockPos, Block block) {
        this.blockPos = blockPos;
        this.block = block;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public Block getBlock() {
        return block;
    }

}
