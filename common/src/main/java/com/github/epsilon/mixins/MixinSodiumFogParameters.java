package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.util.FogParameters", remap = false)
public abstract class MixinSodiumFogParameters {

    @ModifyArgs(method = "<init>(FFFFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/util/FogParameters;<init>(FFFFFFFFF)V"))
    private static void epsilon$hideFog(Args args) {
        if (NoRender.INSTANCE.noFog()) {
            for (int index = 4; index <= 8; index++) args.set(index, Float.MAX_VALUE);
        }
    }

}
