package me.sofurry.via.injection.mixin.features.bedrock.block;

import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
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

@Mixin(CandleCakeBlock.class)
public abstract class MixinCandleCakeBlock extends Block {

    @Unique
    private static final VoxelShape viaVersion$shape_bedrock = Block.column(14.0F, 0.0F, 8.0F);

    @Shadow
    @Final
    private static VoxelShape SHAPE;

    public MixinCandleCakeBlock(final Properties settings) {
        super(settings);
    }

    @Inject(method = "getShape", at = @At(value = "HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            cir.setReturnValue(viaVersion$shape_bedrock);
        }
    }

    @Override
    public @NonNull VoxelShape getOcclusionShape(@NonNull BlockState state) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return SHAPE;
        } else {
            return super.getOcclusionShape(state);
        }
    }

}
