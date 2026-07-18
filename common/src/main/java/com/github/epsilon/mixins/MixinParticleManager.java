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
    private void onCreateParticle(ParticleOptions particleOptions, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> cir) {
        if (!shouldCancel(particleOptions)) {
            return;
        }
        cir.setReturnValue(null);
        cir.cancel();
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void onCreateTrackingEmitter(Entity entity, ParticleOptions particleOptions, int lifetime, CallbackInfo ci) {
        if (!shouldCancel(particleOptions)) {
            return;
        }
        ci.cancel();
    }

    private boolean shouldCancel(ParticleOptions particleOptions) {
        if (particleOptions == null) {
            return false;
        }
        if (particleOptions.getType() == ParticleTypes.TOTEM_OF_UNDYING
                && MasEffects.INSTANCE.shouldHideVanillaTotemParticles()) {
            return true;
        }
        if (!NoRender.INSTANCE.isEnabled()) return false;

        if (NoRender.INSTANCE.explosions.getValue()
                && (particleOptions.getType() == ParticleTypes.EXPLOSION
                || particleOptions.getType() == ParticleTypes.EXPLOSION_EMITTER
                || particleOptions.getType() == ParticleTypes.POOF
                || particleOptions.getType() == ParticleTypes.SMOKE
                || particleOptions.getType() == ParticleTypes.LARGE_SMOKE
                || particleOptions.getType() == ParticleTypes.CLOUD)) {
            return true;
        }

        if (NoRender.INSTANCE.potionParticles.getValue()
                && (particleOptions.getType() == ParticleTypes.EFFECT
                || particleOptions.getType() == ParticleTypes.ENTITY_EFFECT
                || particleOptions.getType() == ParticleTypes.INSTANT_EFFECT
                || particleOptions.getType() == ParticleTypes.SPLASH)) {
            return true;
        }

        if (NoRender.INSTANCE.fireworks.getValue()
                && (particleOptions.getType() == ParticleTypes.FIREWORK || particleOptions.getType() == ParticleTypes.FLASH)) {
            return true;
        }

        if (NoRender.INSTANCE.portal.getValue()
                && (particleOptions.getType() == ParticleTypes.PORTAL || particleOptions.getType() == ParticleTypes.REVERSE_PORTAL)) {
            return true;
        }

        return NoRender.INSTANCE.totems.getValue() && particleOptions.getType() == ParticleTypes.TOTEM_OF_UNDYING;
    }

}
