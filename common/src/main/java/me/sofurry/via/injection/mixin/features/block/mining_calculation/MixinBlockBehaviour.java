package me.sofurry.via.injection.mixin.features.block.mining_calculation;

import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.class)
public abstract class MixinBlockBehaviour {

    @Inject(method = "getDestroyProgress", at = @At("HEAD"), cancellable = true)
    private void changeMiningSpeedCalculation(BlockState state, Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_4_6tor1_4_7)) {
            final float hardness = state.getDestroySpeed(level, pos);
            if (hardness == -1.0F) {
                cir.setReturnValue(0.0F);
            } else {
                if (!player.hasCorrectToolForDrops(state)) {
                    cir.setReturnValue(1.0F / hardness / 100F);
                } else {
                    cir.setReturnValue(player.getDestroySpeed(state) / hardness / 30F);
                }
            }
        }
    }

}
