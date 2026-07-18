package com.github.epsilon.fabric.mixins.sodium;

import com.github.epsilon.modules.impl.render.Xray;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractBlockRenderContext.class, remap = false)
public class MixinSodiumAbstractBlockRenderContext {

    @Shadow
    protected BlockState state;

    @Inject(method = "isFaceCulled", at = @At("HEAD"), cancellable = true)
    private void hookIsFaceCulled(Direction face, CallbackInfoReturnable<Boolean> cir) {
        Xray xray = Xray.INSTANCE;
        if (xray.isEnabled() && xray.wallHack.getValue()) {
            cir.setReturnValue(!xray.isCheckableOre(this.state.getBlock()));
        }
    }

}
