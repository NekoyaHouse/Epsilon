package me.sofurry.via.features.block.connections;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;

public final class WallStateHandler implements IBlockStateHandler {

    @Override
    public BlockState connect(final BlockState blockState, final LevelReader levelReader, final BlockPos blockPos) {
        final boolean north = connectsTo(levelReader, blockPos.north(), Direction.NORTH);
        final boolean south = connectsTo(levelReader, blockPos.south(), Direction.SOUTH);
        final boolean west = connectsTo(levelReader, blockPos.west(), Direction.WEST);
        final boolean east = connectsTo(levelReader, blockPos.east(), Direction.EAST);
        final boolean up = !(south && !west && north && !east || !south && west && !north && east) || !levelReader.getBlockState(blockPos.above()).isAir();
        return blockState
                .setValue(WallBlock.UP, up)
                .setValue(WallBlock.NORTH, getWallSide(north))
                .setValue(WallBlock.SOUTH, getWallSide(south))
                .setValue(WallBlock.WEST, getWallSide(west))
                .setValue(WallBlock.EAST, getWallSide(east));
    }

    // TODO: Fine-tune and make perfect/1:1
    private boolean connectsTo(final BlockGetter blockGetter, final BlockPos blockPos, final Direction direction) {
        final BlockState neighborState = blockGetter.getBlockState(blockPos);

        final Block block = neighborState.getBlock();
        if (block instanceof StairBlock) {
            // TODO: Sometimes isn't right
            return neighborState.getValue(StairBlock.FACING) == direction.getOpposite(); // Only connect to the backside of stairs
        }

        return !isExceptionForConnection(neighborState) && (block instanceof WallBlock || block instanceof FenceGateBlock || block instanceof SlimeBlock || neighborState.isSolidRender());
    }

    private WallSide getWallSide(final boolean value) {
        return value ? WallSide.LOW : WallSide.NONE;
    }

}
