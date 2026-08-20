package me.sofurry.via.injection.mixin.features.bedrock.block;

import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BambooStalkBlock.class)
public abstract class MixinBambooStalkBlock {

    @Shadow
    @Final
    public static int AGE_THICK_BAMBOO;

    @Shadow
    @Final
    public static IntegerProperty AGE;

    @Unique
    private static final VoxelShape viaVersion$shape_small_bedrock = Block.box(8D, 0.0D, 8D, 10D, 16.0D, 10D);

    @Unique
    private static final VoxelShape viaVersion$shape_large_bedrock = Block.box(8D, 0.0D, 8D, 11D, 16.0D, 11D);

    @Inject(method = {"getShape", "getCollisionShape"}, at = @At("HEAD"), cancellable = true)
    private void fixBambooShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            final VoxelShape voxelShape = state.getValue(AGE) == AGE_THICK_BAMBOO ? viaVersion$shape_large_bedrock : viaVersion$shape_small_bedrock;
            cir.setReturnValue(voxelShape.move(state.getOffset(pos)));
        }
    }

}
