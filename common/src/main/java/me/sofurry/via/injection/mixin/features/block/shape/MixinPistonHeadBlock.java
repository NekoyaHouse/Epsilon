package me.sofurry.via.injection.mixin.features.block.shape;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonHeadBlock.class)
public abstract class MixinPistonHeadBlock extends DirectionalBlock {

    @Unique
    private static final VoxelShape viaVersion$east_head_shape = Block.box(12.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    @Unique
    private static final VoxelShape viaVersion$west_head_shape = Block.box(0.0, 0.0, 0.0, 4.0, 16.0, 16.0);

    @Unique
    private static final VoxelShape viaVersion$south_head_shape = Block.box(0.0, 0.0, 12.0, 16.0, 16.0, 16.0);

    @Unique
    private static final VoxelShape viaVersion$north_head_shape = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 4.0);

    @Unique
    private static final VoxelShape viaVersion$up_head_shape = Block.box(0.0, 12.0, 0.0, 16.0, 16.0, 16.0);

    @Unique
    private static final VoxelShape viaVersion$down_head_shape = Block.box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0);

    @Unique
    private static final VoxelShape viaVersion$up_arm_shape_r1_8_x = Block.box(6.0, 0.0, 6.0, 10.0, 12.0, 10.0);

    @Unique
    private static final VoxelShape viaVersion$down_arm_shape_r1_8_x = Block.box(6.0, 4.0, 6.0, 10.0, 16.0, 10.0);

    @Unique
    private static final VoxelShape viaVersion$south_arm_shape_r1_8_x = Block.box(4.0, 6.0, 0.0, 12.0, 10.0, 12.0);

    @Unique
    private static final VoxelShape viaVersion$north_arm_shape_r1_8_x = Block.box(4.0, 6.0, 4.0, 12.0, 10.0, 16.0);

    @Unique
    private static final VoxelShape viaVersion$east_arm_shape_r1_8_x = Block.box(0.0, 6.0, 4.0, 12.0, 10.0, 12.0);

    @Unique
    private static final VoxelShape viaVersion$west_arm_shape_r1_8_x = Block.box(6.0, 4.0, 4.0, 10.0, 12.0, 16.0);

    @Unique
    private boolean viaVersion$selfInflicted = false;

    protected MixinPistonHeadBlock(Properties settings) {
        super(settings);
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (viaVersion$selfInflicted) {
            viaVersion$selfInflicted = false;
            return;
        }
        // Outline shape for piston head doesn't exist in <= 1.12.2
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            cir.setReturnValue(switch (state.getValue(PistonHeadBlock.FACING)) {
                case DOWN -> viaVersion$down_head_shape;
                case UP -> viaVersion$up_head_shape;
                case NORTH -> viaVersion$north_head_shape;
                case SOUTH -> viaVersion$south_head_shape;
                case WEST -> viaVersion$west_head_shape;
                case EAST -> viaVersion$east_head_shape;
            });
        }
    }

    @Override
    public @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter world, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return switch (state.getValue(PistonHeadBlock.FACING)) {
                case DOWN -> Shapes.or(viaVersion$down_head_shape, viaVersion$down_arm_shape_r1_8_x);
                case UP -> Shapes.or(viaVersion$up_head_shape, viaVersion$up_arm_shape_r1_8_x);
                case NORTH -> Shapes.or(viaVersion$north_head_shape, viaVersion$north_arm_shape_r1_8_x);
                case SOUTH -> Shapes.or(viaVersion$south_head_shape, viaVersion$south_arm_shape_r1_8_x);
                case WEST -> Shapes.or(viaVersion$west_head_shape, viaVersion$west_arm_shape_r1_8_x);
                case EAST -> Shapes.or(viaVersion$east_head_shape, viaVersion$east_arm_shape_r1_8_x);
            };
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            // Collision shape for piston head in <= 1.12.2 needs to be the 1.13+ outline shape
            viaVersion$selfInflicted = true;
            return getShape(state, world, pos, context);
        } else {
            return super.getCollisionShape(state, world, pos, context);
        }
    }

}
