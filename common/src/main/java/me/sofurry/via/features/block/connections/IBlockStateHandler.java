package me.sofurry.via.features.block.connections;

import me.sofurry.via.features.block.interaction.Block1_14;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public interface IBlockStateHandler {
    BlockState connect(final BlockState blockState, final LevelReader levelReader, final BlockPos blockPos);

    default boolean isExceptionForConnection(final BlockState blockState) {
        return blockState.isAir()
                || Block1_14.isExceptBlockForAttachWithPiston(blockState.getBlock())
                || blockState.is(Blocks.MELON)
                || blockState.is(Blocks.PUMPKIN)
                || blockState.is(Blocks.CARVED_PUMPKIN)
                || blockState.is(Blocks.JACK_O_LANTERN)
                || blockState.is(Blocks.BARRIER);
    }
}
