package me.sofurry.via.features.block.connections;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;

// Code sourced and adapted from 1.12.2 (Feather)
public class PipeStateHandler implements IBlockStateHandler {

    @Override
    public BlockState connect(final BlockState blockState, final LevelReader levelReader, final BlockPos blockPos) {
        final Block block = blockState.getBlock();
        final BlockState belowState = levelReader.getBlockState(blockPos.below());
        final BlockState aboveState = levelReader.getBlockState(blockPos.above());
        final BlockState northState = levelReader.getBlockState(blockPos.north());
        final BlockState eastState = levelReader.getBlockState(blockPos.east());
        final BlockState southState = levelReader.getBlockState(blockPos.south());
        final BlockState westState = levelReader.getBlockState(blockPos.west());
        return blockState.setValue(PipeBlock.DOWN, belowState.is(block) || belowState.is(Blocks.CHORUS_FLOWER) || belowState.is(Blocks.END_STONE))
                .setValue(PipeBlock.UP, aboveState.is(block) || aboveState.is(Blocks.CHORUS_FLOWER))
                .setValue(PipeBlock.NORTH, northState.is(block) || northState.is(Blocks.CHORUS_FLOWER))
                .setValue(PipeBlock.EAST, eastState.is(block) || eastState.is(Blocks.CHORUS_FLOWER))
                .setValue(PipeBlock.SOUTH, southState.is(block) || southState.is(Blocks.CHORUS_FLOWER))
                .setValue(PipeBlock.WEST, westState.is(block) || westState.is(Blocks.CHORUS_FLOWER));
    }

}
