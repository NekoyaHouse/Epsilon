package com.github.epsilon.neoforge.mixins;

import me.sofurry.via.features.world.footstep_particle.FootStepParticle1_12_2;
import net.neoforged.neoforge.registries.GameData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameData.class, remap = false)
public abstract class MixinGameData {

    @Inject(method = "postRegisterEvents", at = @At("HEAD"))
    private static void registerViaVersionParticle(final CallbackInfo ci) {
        FootStepParticle1_12_2.init();
    }

}
