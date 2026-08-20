package me.sofurry.via.injection.mixin.features.block.shape;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import me.sofurry.via.injection.access.block.shape.ICrossCollisionBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CrossCollisionBlock.class)
public abstract class MixinCrossCollisionBlock implements ICrossCollisionBlock {

    @Unique
    private final Object2IntMap<BlockState> viaVersion$SHAPE_INDEX_CACHE = new Object2IntOpenHashMap<>();

    @Override
    public int viaVersion$getShapeIndex(final BlockState blockState) {
        return viaVersion$SHAPE_INDEX_CACHE.computeIfAbsent(blockState, _ -> {
            int index = 0;
            if (blockState.getValue(CrossCollisionBlock.NORTH)) {
                index |= 1 << Direction.NORTH.get2DDataValue();
            }
            if (blockState.getValue(CrossCollisionBlock.EAST)) {
                index |= 1 << Direction.EAST.get2DDataValue();
            }
            if (blockState.getValue(CrossCollisionBlock.SOUTH)) {
                index |= 1 << Direction.SOUTH.get2DDataValue();
            }
            if (blockState.getValue(CrossCollisionBlock.WEST)) {
                index |= 1 << Direction.WEST.get2DDataValue();
            }

            return index;
        });
    }

}
