package me.sofurry.via.injection.mixin;

import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LanternBlock.class)
public abstract class MixinLanternBlock extends Block {

    @Unique
    private static final VoxelShape viaVersion$shape_bedrock = Shapes.box(0.3125, 0, 0.3125, 0.6875, 0.5, 0.6875);

    @Unique
    private static final VoxelShape viaVersion$shape_hanging_bedrock = Shapes.box(0.3125, 0.125, 0.3125, 0.6875, 0.625, 0.6875);

    @Shadow
    @Final
    public static BooleanProperty HANGING;

    @Shadow
    @Final
    private static VoxelShape SHAPE_STANDING;

    @Shadow
    @Final
    private static VoxelShape SHAPE_HANGING;

    public MixinLanternBlock(final Properties settings) {
        super(settings);
    }

    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true)
    private void modifyCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            cir.setReturnValue(state.getValue(HANGING) ? viaVersion$shape_hanging_bedrock : viaVersion$shape_bedrock);
        }
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return state.getValue(HANGING) ? SHAPE_HANGING : SHAPE_STANDING;
        } else {
            return super.getOcclusionShape(state);
        }
    }

}
