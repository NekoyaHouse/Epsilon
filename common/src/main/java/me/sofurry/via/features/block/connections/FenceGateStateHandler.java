package me.sofurry.via.features.block.connections;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

// Code sourced and adapted from 1.12.2 (Feather)
public final class FenceGateStateHandler implements IBlockStateHandler {

    @Override
    public BlockState connect(final BlockState blockState, final LevelReader levelReader, final BlockPos blockPos) {
        final Direction.Axis axis = blockState.getValue(FenceGateBlock.FACING).getAxis();
        final BlockState westState = levelReader.getBlockState(blockPos.west());
        final BlockState eastState = levelReader.getBlockState(blockPos.east());
        final BlockState northState = levelReader.getBlockState(blockPos.north());
        final BlockState southState = levelReader.getBlockState(blockPos.south());
        if (axis == Direction.Axis.Z && (westState.is(Blocks.COBBLESTONE_WALL) || eastState.is(Blocks.COBBLESTONE_WALL))
                || axis == Direction.Axis.X && (northState.is(Blocks.COBBLESTONE_WALL) || southState.is(Blocks.COBBLESTONE_WALL))) {
            return blockState.setValue(FenceGateBlock.IN_WALL, true);
        } else {
            return blockState;
        }
    }

}
