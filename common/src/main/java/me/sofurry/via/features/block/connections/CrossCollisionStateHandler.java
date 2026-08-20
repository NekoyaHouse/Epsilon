package me.sofurry.via.features.block.connections;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

// Handles [Fences, Iron Bars, Glass Panes]
public record CrossCollisionStateHandler(Predicate<BlockState> customAllowed) implements IBlockStateHandler {

    @Override
    public BlockState connect(final BlockState blockState, final LevelReader levelReader, final BlockPos blockPos) {
        return blockState
                .setValue(CrossCollisionBlock.NORTH, connectsTo(levelReader, blockPos.north(), Direction.NORTH))
                .setValue(CrossCollisionBlock.SOUTH, connectsTo(levelReader, blockPos.south(), Direction.SOUTH))
                .setValue(CrossCollisionBlock.WEST, connectsTo(levelReader, blockPos.west(), Direction.WEST))
                .setValue(CrossCollisionBlock.EAST, connectsTo(levelReader, blockPos.east(), Direction.EAST));
    }

    private boolean connectsTo(final BlockGetter blockGetter, final BlockPos blockPos, final Direction direction) {
        final BlockState neighborState = blockGetter.getBlockState(blockPos);

        boolean bl = false;
        if (neighborState.is(BlockTags.STAIRS)) {
            // TODO: Sometimes isn't right
            bl = neighborState.getValue(StairBlock.FACING) == direction.getOpposite(); // Only connect to the backside of stairs
        }

        return !isExceptionForConnection(neighborState) && (this.customAllowed.test(neighborState) || bl || neighborState.isSolidRender());
    }

}
