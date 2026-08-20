package me.sofurry.via.features.block.connections;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class RedStoneStateHandler implements IBlockStateHandler {

    @Override
    public BlockState connect(final BlockState blockState, final LevelReader levelReader, final BlockPos blockPos) {
        final RedStoneWireBlock redStoneWireBlock = (RedStoneWireBlock) blockState.getBlock();
        return blockState.setValue(RedStoneWireBlock.WEST, redStoneWireBlock.getConnectingSide(levelReader, blockPos, Direction.WEST))
                .setValue(RedStoneWireBlock.EAST, redStoneWireBlock.getConnectingSide(levelReader, blockPos, Direction.EAST))
                .setValue(RedStoneWireBlock.NORTH, redStoneWireBlock.getConnectingSide(levelReader, blockPos, Direction.NORTH))
                .setValue(RedStoneWireBlock.SOUTH, redStoneWireBlock.getConnectingSide(levelReader, blockPos, Direction.SOUTH));
    }

}
