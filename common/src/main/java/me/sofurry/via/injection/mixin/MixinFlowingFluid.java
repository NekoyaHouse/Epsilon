package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import me.sofurry.via.protocoltranslator.impl.ViaVersionMappingDataLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FlowingFluid.class)
public abstract class MixinFlowingFluid {

    @Redirect(method = "isSolidFace", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isFaceSturdy(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"))
    private boolean modifyIsSolidBlock(BlockState instance, BlockGetter blockView, BlockPos blockPos, Direction direction) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_11_1)) {
            final ViaVersionMappingDataLoader.Material material = ViaVersionMappingDataLoader.MATERIALS.get(ViaVersionMappingDataLoader.getBlockMaterial(instance.getBlock()));
            return material.solid();
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            final Block block = instance.getBlock();
            if (block instanceof ShulkerBoxBlock || block instanceof LeavesBlock || block instanceof TrapDoorBlock ||
                    block == Blocks.BEACON || block == Blocks.CAULDRON || block == Blocks.GLASS ||
                    block == Blocks.GLOWSTONE || block == Blocks.ICE || block == Blocks.SEA_LANTERN ||
                    block instanceof StainedGlassBlock || block == Blocks.PISTON || block == Blocks.STICKY_PISTON ||
                    block == Blocks.PISTON_HEAD || block instanceof StairBlock) {
                return false;
            }
        }
        return instance.isFaceSturdy(blockView, blockPos, direction);
    }

}
