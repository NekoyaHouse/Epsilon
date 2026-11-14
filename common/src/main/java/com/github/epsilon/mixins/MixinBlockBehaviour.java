package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.class)
public abstract class MixinBlockBehaviour {

    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void epsilon$constantSeed(CallbackInfoReturnable<Long> cir) {
        if (NoRender.INSTANCE.noTextureRotations()) cir.setReturnValue(0L);
    }

}
