package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.platform.ViaVersionPlatform;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlock.class)
public abstract class MixinHopperBlock extends BaseEntityBlock {

    @Unique
    private static final VoxelShape viaVersion$inside_shape_r1_12_2 = Block.box(2.0D, 10.0D, 2.0D, 14.0D, 16.0D, 14.0D);

    @Unique
    private static final VoxelShape viaVersion$hopper_shape_r1_12_2 = Shapes.join(Shapes.block(), viaVersion$inside_shape_r1_12_2, BooleanOp.ONLY_FIRST);

    @Unique
    private boolean viaVersion$requireOriginalShape;

    public MixinHopperBlock(Properties settings) {
        super(settings);
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ViaVersionPlatform.isModLoaded("moreculling") && viaVersion$requireOriginalShape) {
            viaVersion$requireOriginalShape = false;
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            cir.setReturnValue(viaVersion$hopper_shape_r1_12_2);
        }
    }

    @Inject(method = "getInteractionShape", at = @At("HEAD"), cancellable = true)
    private void changeRaycastShape(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            cir.setReturnValue(viaVersion$inside_shape_r1_12_2);
        }
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state) {
        // Workaround for https://github.com/ViaVersion/ViaFabricPlus/issues/45
        viaVersion$requireOriginalShape = true;
        return super.getOcclusionShape(state);
    }

}
