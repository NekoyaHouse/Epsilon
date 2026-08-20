package me.sofurry.via.injection.mixin.features.block.shape;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.injection.ViaVersionMixinPlugin;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilBlock.class)
public abstract class MixinAnvilBlock extends FallingBlock {

    @Unique
    private static final VoxelShape viaVersion$x_axis_shape_r1_12_2 = Block.box(0.0D, 0.0D, 2.0D, 16.0D, 16.0D, 14.0D);

    @Unique
    private static final VoxelShape viaVersion$z_axis_shape_r1_12_2 = Block.box(2.0D, 0.0D, 0.0D, 14.0D, 16.0D, 16.0D);

    @Shadow
    @Final
    public static EnumProperty<Direction> FACING;

    public MixinAnvilBlock(Properties settings) {
        super(settings);
    }

    @Unique
    private boolean viaVersion$requireOriginalShape;

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ViaVersionMixinPlugin.MORE_CULLING_PRESENT && viaVersion$requireOriginalShape) {
            viaVersion$requireOriginalShape = false;
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            cir.setReturnValue(state.getValue(FACING).getAxis() == Direction.Axis.X ? viaVersion$x_axis_shape_r1_12_2 : viaVersion$z_axis_shape_r1_12_2);
        }
    }

    @Override
    protected @NonNull VoxelShape getOcclusionShape(BlockState state) {
        // Workaround for https://github.com/ViaVersion/ViaFabricPlus/issues/246
        // MoreCulling is caching the culling shape and doesn't reload it, so we have to force vanilla's shape here.
        viaVersion$requireOriginalShape = true;
        return super.getOcclusionShape(state);
    }

}
