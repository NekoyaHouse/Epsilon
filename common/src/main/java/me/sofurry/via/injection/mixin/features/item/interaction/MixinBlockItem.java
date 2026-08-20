package me.sofurry.via.injection.mixin.features.item.interaction;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class MixinBlockItem {

    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void checkChestPlacement(BlockPlaceContext context, BlockState stateForPlacement, CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            Block block = stateForPlacement.getBlock();
            if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
                Level world = context.getLevel();
                BlockPos pos = context.getClickedPos();
                boolean foundAdjChest = false;
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockState otherState = world.getBlockState(pos.relative(dir));
                    if (otherState.getBlock() == block) {
                        if (foundAdjChest) {
                            cir.setReturnValue(false);
                            return;
                        }
                        foundAdjChest = true;
                        if (otherState.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                            cir.setReturnValue(false);
                            return;
                        }
                    }
                }
            }
        }
    }

}
