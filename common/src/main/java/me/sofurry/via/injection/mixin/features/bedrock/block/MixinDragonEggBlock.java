package me.sofurry.via.injection.mixin.features.bedrock.block;

import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DragonEggBlock.class)
public abstract class MixinDragonEggBlock extends Block {

    @Shadow
    @Final
    private static VoxelShape SHAPE;

    public MixinDragonEggBlock(final Properties settings) {
        super(settings);
    }

    @Inject(method = "getShape", at = @At(value = "RETURN"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            cir.setReturnValue(Shapes.block());
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
