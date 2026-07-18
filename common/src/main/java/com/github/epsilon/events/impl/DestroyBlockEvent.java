package com.github.epsilon.events.impl;

import com.github.epsilon.events.bus.Cancellable;
import net.minecraft.core.BlockPos;

public class DestroyBlockEvent extends Cancellable {

    private final BlockPos pos;

    public DestroyBlockEvent(BlockPos pos) {
        this.pos = pos;
    }

    public BlockPos getPos() {
        return this.pos;
    }

}
