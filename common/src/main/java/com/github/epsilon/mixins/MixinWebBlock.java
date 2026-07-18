package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.movement.FastWeb;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.github.epsilon.Constants.mc;

@Mixin(WebBlock.class)
public class MixinWebBlock {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void onEntityCollision(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise, CallbackInfo ci) {
        if (entity == mc.player && FastWeb.INSTANCE.cobweb()) {
            ci.cancel();
        }
    }

}
