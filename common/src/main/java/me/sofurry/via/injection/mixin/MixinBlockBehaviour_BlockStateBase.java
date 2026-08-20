package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinBlockBehaviour_BlockStateBase {

    @Shadow
    public abstract Block getBlock();

    @Shadow
    @Final
    private boolean requiresCorrectToolForDrops;

    /**
     * @author RK_01
     * @reason Change break speed for shulker blocks in < 1.14
     */
    @Overwrite
    public boolean requiresCorrectToolForDrops() {
        if (this.getBlock() instanceof ShulkerBoxBlock && ProtocolTranslator.getTargetVersion().olderThan(ProtocolVersion.v1_14)) {
            return true;
        } else {
            return this.requiresCorrectToolForDrops;
        }
    }

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void changeHardness(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        final Block block = this.getBlock();

        if (block.equals(Blocks.END_STONE_BRICKS) || block.equals(Blocks.END_STONE_BRICK_SLAB) || block.equals(Blocks.END_STONE_BRICK_STAIRS) || block.equals(Blocks.END_STONE_BRICK_WALL)) {
            if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4)) {
                cir.setReturnValue(0.8F);
            }
        } else if (block.equals(Blocks.PISTON) || block.equals(Blocks.STICKY_PISTON) || block.equals(Blocks.PISTON_HEAD)) {
            if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2)) {
                cir.setReturnValue(0.5F);
            }
        } else if (block instanceof InfestedBlock) {
            if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
                cir.setReturnValue(0.75F);
            } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_16_4)) {
                cir.setReturnValue(0F);
            }
        } else if (block.equals(Blocks.OBSIDIAN)) {
            if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.b1_8tob1_8_1)) {
                cir.setReturnValue(10.0F);
            }
        }
    }

}
