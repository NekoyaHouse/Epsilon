package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.FireworkParticles;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {FireworkParticles.SparkParticle.class, FireworkParticles.OverlayParticle.class})
public class MixinFireworksSparkParticleSub {

    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void buildExplosionGeometry(QuadParticleRenderState particleTypeRenderState, Camera camera, float partialTickTime, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.fireworkExplosions.getValue()) ci.cancel();
    }

}
