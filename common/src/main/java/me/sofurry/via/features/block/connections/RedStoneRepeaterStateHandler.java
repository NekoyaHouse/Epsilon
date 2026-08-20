package me.sofurry.via.features.block.connections;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class RedStoneRepeaterStateHandler implements IBlockStateHandler {

    @Override
    public BlockState connect(final BlockState blockState, final LevelReader levelReader, final BlockPos blockPos) {
        return blockState.setValue(RepeaterBlock.LOCKED, ((RepeaterBlock) blockState.getBlock()).isLocked(levelReader, blockPos, blockState));
    }

}
