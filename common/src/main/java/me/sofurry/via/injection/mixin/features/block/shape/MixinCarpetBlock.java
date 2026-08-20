package me.sofurry.via.injection.mixin.features.block.shape;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CarpetBlock.class)
public abstract class MixinCarpetBlock extends Block {

    @Unique
    private static final VoxelShape viaVersion$shape_r1_7_10 = Block.box(0.0D, -0.00001D /* 0.0D */, 0.0D, 16.0D, 0.0D, 16.0D);

    public MixinCarpetBlock(Properties settings) {
        super(settings);
    }

    @Override
    public @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter world, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_7_6)) {
            return viaVersion$shape_r1_7_10;
        } else {
            return super.getCollisionShape(state, world, pos, context);
        }
    }

}
