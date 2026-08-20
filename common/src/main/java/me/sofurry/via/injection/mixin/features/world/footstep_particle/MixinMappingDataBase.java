package me.sofurry.via.injection.mixin.features.world.footstep_particle;

import com.viaversion.viaversion.api.data.MappingDataBase;
import me.sofurry.via.features.world.footstep_particle.FootStepParticle1_12_2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MappingDataBase.class, remap = false)
public abstract class MixinMappingDataBase {

    @Inject(method = "getNewParticleId", at = @At("HEAD"), cancellable = true)
    private void passthroughFootStepParticle(int id, CallbackInfoReturnable<Integer> cir) {
        if (id == FootStepParticle1_12_2.RAW_ID) {
            cir.setReturnValue(id);
        }
    }

}
