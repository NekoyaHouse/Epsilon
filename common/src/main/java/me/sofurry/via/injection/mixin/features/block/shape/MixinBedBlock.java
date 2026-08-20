package me.sofurry.via.injection.mixin.features.block.shape;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.injection.ViaVersionMixinPlugin;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BedBlock.class)
public abstract class MixinBedBlock extends HorizontalDirectionalBlock {

    @Unique
    private static final VoxelShape viaVersion$shape_r1_13_2 = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 9.0D, 16.0D);
    @Unique
    private boolean viaVersion$requireOriginalShape;

    protected MixinBedBlock(Properties settings) {
        super(settings);
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ViaVersionMixinPlugin.MORE_CULLING_PRESENT && viaVersion$requireOriginalShape) {
            viaVersion$requireOriginalShape = false;
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2) || ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            cir.setReturnValue(viaVersion$shape_r1_13_2);
        }
    }

    @Override
    public @NonNull VoxelShape getOcclusionShape(@NonNull BlockState state) {
        // Workaround for https://github.com/ViaVersion/ViaFabricPlus/issues/246
        // MoreCulling is caching the culling shape and doesn't reload it, so we have to force vanilla's shape here.
        viaVersion$requireOriginalShape = true;
        return super.getOcclusionShape(state);
    }

}
