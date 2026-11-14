package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinBlockState {

    @Inject(method = "getOffset", at = @At("HEAD"), cancellable = true)
    private void epsilon$constantOffset(CallbackInfoReturnable<Vec3> cir) {
        if (NoRender.INSTANCE.noTextureRotations()) cir.setReturnValue(Vec3.ZERO);
    }

}
