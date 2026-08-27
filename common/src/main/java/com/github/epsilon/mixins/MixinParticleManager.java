package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import com.github.epsilon.modules.impl.render.maseffects.MasEffects;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class MixinParticleManager {

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true, require = 0)
    private void onCreateParticle(ParticleOptions options, double x, double y, double z, double xa, double ya, double za, CallbackInfoReturnable<Particle> cir) {
        if (shouldCancel(options)) cir.setReturnValue(null);
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void onCreateTrackingEmitter(Entity entity, ParticleOptions particle, int lifeTime, CallbackInfo ci) {
        if (shouldCancel(particle)) ci.cancel();
    }

    private boolean shouldCancel(ParticleOptions particleOptions) {
        if (particleOptions == null) return false;
        var type = particleOptions.getType();
        if (type == ParticleTypes.FLAME) return true; // LoyisaIsImposter
        if (type == ParticleTypes.TOTEM_OF_UNDYING && MasEffects.INSTANCE.shouldHideVanillaTotemParticles())
            return true;
        if (NoRender.INSTANCE.isEnabled()) {
            if (NoRender.INSTANCE.weather.getValue() && type == ParticleTypes.RAIN) return true;
            if (NoRender.INSTANCE.fireworkExplosions.getValue() && type == ParticleTypes.FIREWORK) return true;
        }
        return false;
    }

}
