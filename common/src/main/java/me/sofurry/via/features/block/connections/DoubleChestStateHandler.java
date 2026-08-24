package me.sofurry.via.features.block.connections;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public class DoubleChestStateHandler implements IBlockStateHandler {

    @Override
    public BlockState connect(final BlockState blockState, final LevelReader levelReader, final BlockPos blockPos) {
        if (!blockState.is(Blocks.ENDER_CHEST)) { // Ignore Ender-chests
            return blockState.setValue(ChestBlock.TYPE, getChestType(blockState, levelReader, blockPos));
        } else {
            return blockState;
        }
    }

    private ChestType getChestType(final BlockState blockState, final BlockGetter blockGetter, final BlockPos blockPos) {
        final Direction facing = blockState.getValue(ChestBlock.FACING);
        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            final BlockState neighborState = blockGetter.getBlockState(blockPos.relative(direction));
            if (neighborState.is(blockState.getBlock()) && neighborState.getValue(ChestBlock.FACING).equals(facing)) {
                if (direction == facing.getClockWise()) {
                    return ChestType.LEFT;
                } else if (direction == facing.getCounterClockWise()) {
                    return ChestType.RIGHT;
                }
            }
        }

        return ChestType.SINGLE;
    }

}
