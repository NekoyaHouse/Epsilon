package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class MixinSodiumWorldRenderer {

    @ModifyVariable(method = "setupTerrain", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean epsilon$disableCaveCulling(boolean original) {
        return original && !NoRender.INSTANCE.noCaveCulling();
    }

}
