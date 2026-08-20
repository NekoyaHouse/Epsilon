package me.sofurry.via.injection.mixin.features.block.shape;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndPortalFrameBlock.class)
public abstract class MixinEndPortalFrameBlock extends Block {

    @Shadow
    @Final
    private static VoxelShape SHAPE_EMPTY;

    @Shadow
    @Final
    public static BooleanProperty HAS_EYE;

    @Unique
    private static final VoxelShape viaVersion$eye_shape_r1_12_2 = Block.box(5.0D, 13.0D, 5.0D, 11.0D, 16.0D, 11.0D);

    @Unique
    private static final VoxelShape viaVersion$frame_with_eye_shape_r1_12_2 = Shapes.or(SHAPE_EMPTY, viaVersion$eye_shape_r1_12_2);

    @Unique
    private static final VoxelShape viaVersion$shape_frame_bedrock = Shapes.box(0, 0, 0, 1, 0.8125, 1);

    public MixinEndPortalFrameBlock(Properties settings) {
        super(settings);
    }

    @Inject(method = "getShape", at = @At(value = "HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            cir.setReturnValue(SHAPE_EMPTY);
        } else if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            // The eye doesn't have a different shape on bedrock
            cir.setReturnValue(viaVersion$shape_frame_bedrock);
        }
    }

    @Override
    public @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter world, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            return state.getValue(HAS_EYE) ? viaVersion$frame_with_eye_shape_r1_12_2 : SHAPE_EMPTY;
        } else {
            return super.getCollisionShape(state, world, pos, context);
        }
    }

    @Override
    public @NonNull VoxelShape getOcclusionShape(@NonNull BlockState state) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return SHAPE_EMPTY;
        } else {
            return super.getOcclusionShape(state);
        }
    }

}
