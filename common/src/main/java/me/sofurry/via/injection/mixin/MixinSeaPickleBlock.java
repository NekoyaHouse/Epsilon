package me.sofurry.via.injection.mixin;

import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
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

@Mixin(SeaPickleBlock.class)
public abstract class MixinSeaPickleBlock extends Block {

    @Unique
    private static final VoxelShape viaVersion$shape_bedrock = Block.column(16.0F, 0.0F, 6.0F);

    @Shadow
    @Final
    private static VoxelShape SHAPE_ONE;

    @Shadow
    @Final
    private static VoxelShape SHAPE_TWO;

    @Shadow
    @Final
    private static VoxelShape SHAPE_THREE;

    @Shadow
    @Final
    private static VoxelShape SHAPE_FOUR;

    @Shadow
    @Final
    public static IntegerProperty PICKLES;

    public MixinSeaPickleBlock(final Properties settings) {
        super(settings);
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            cir.setReturnValue(viaVersion$shape_bedrock);
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return Shapes.empty();
        } else {
            return super.getCollisionShape(state, world, pos, context);
        }
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return switch (state.getValue(PICKLES)) {
                case 2 -> SHAPE_TWO;
                case 3 -> SHAPE_THREE;
                case 4 -> SHAPE_FOUR;
                default -> SHAPE_ONE;
            };
        } else {
            return super.getOcclusionShape(state);
        }
    }
}
