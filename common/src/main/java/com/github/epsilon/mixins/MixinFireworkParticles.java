package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import net.minecraft.client.particle.FireworkParticles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({FireworkParticles.SparkParticle.class, FireworkParticles.OverlayParticle.class})
public abstract class MixinFireworkParticles {

    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void epsilon$hideExplosion(CallbackInfo ci) {
        if (NoRender.INSTANCE.noFireworkExplosions()) ci.cancel();
    }

}
