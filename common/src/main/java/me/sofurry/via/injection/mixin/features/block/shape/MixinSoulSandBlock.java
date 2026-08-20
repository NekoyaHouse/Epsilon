package me.sofurry.via.injection.mixin.features.block.shape;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SoulSandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoulSandBlock.class)
public abstract class MixinSoulSandBlock {

    @Inject(method = "getBlockSupportShape", at = @At("HEAD"), cancellable = true)
    private void changeSidesShape(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        if (ProtocolTranslator.getTargetVersion().betweenInclusive(ProtocolVersion.v1_13, ProtocolVersion.v1_15_2)) {
            cir.setReturnValue(Shapes.empty());
        }
    }

}
