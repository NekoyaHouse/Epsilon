/*
 * This file is part of ViaVersion - https://github.com/ViaVersion/ViaFabricPlus
 * Copyright (C) 2021-2026 the original authors
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package me.sofurry.via.injection.mixin.core.integration.particles;

import me.sofurry.via.features.world.footstep_particle.FootStepParticle1_12_2;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleResources.class)
public abstract class MixinParticleResources {

    @Invoker("register")
    protected abstract <T extends ParticleOptions> void viaVersion$register(ParticleType<T> type, ParticleProvider<T> provider);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void viaVersion$registerFootstepProvider(final CallbackInfo ci) {
        FootStepParticle1_12_2.init();
        viaVersion$register(FootStepParticle1_12_2.TYPE, new FootStepParticle1_12_2.Factory());
    }

}
