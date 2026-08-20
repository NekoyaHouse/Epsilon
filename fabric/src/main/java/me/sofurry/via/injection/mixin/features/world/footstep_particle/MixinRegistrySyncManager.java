/*
 * This file is part of ViaVersion - https://github.com/ViaVersion/ViaFabricPlus
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package me.sofurry.via.injection.mixin.features.world.footstep_particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.sofurry.via.features.world.footstep_particle.FootStepParticle1_12_2;
import net.fabricmc.fabric.impl.registry.sync.RegistrySyncManager;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RegistrySyncManager.class)
public abstract class MixinRegistrySyncManager {

    @WrapOperation(method = "createAndPopulateRegistryMap", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Registry;getKey(Ljava/lang/Object;)Lnet/minecraft/resources/Identifier;"), require = 0)
    private static Identifier skipFootStepParticle(Registry<?> instance, Object t, Operation<Identifier> original) {
        final Identifier id = original.call(instance, t);
        if (id == FootStepParticle1_12_2.ID) {
            return null;
        } else {
            return id;
        }
    }

}
