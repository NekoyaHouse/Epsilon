package me.sofurry.via.features.block.connections;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

// Code sourced and adapted from 1.12.2 (Feather)
public final class StairsStateHandler implements IBlockStateHandler {

    @Override
    public BlockState connect(final BlockState blockState, final LevelReader levelReader, final BlockPos blockPos) {
        return blockState.setValue(StairBlock.SHAPE, StairBlock.getStairsShape(blockState, levelReader, blockPos));
    }

}
