package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.maseffects.MasEffects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.GustSeedParticle;
import net.minecraft.client.particle.NoRenderParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GustSeedParticle.class)
public abstract class MixinGustSeedParticle extends NoRenderParticle {

    protected MixinGustSeedParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (this.age == 0) {
            MasEffects.INSTANCE.onWindSeed(this.x, this.y, this.z);
        }
    }

}
