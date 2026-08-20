package me.sofurry.via.injection.mixin.features.world.footstep_particle;

import com.viaversion.viaversion.protocols.v1_12_2to1_13.data.ParticleIdMappings1_13;
import me.sofurry.via.features.world.footstep_particle.FootStepParticle1_12_2;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = ParticleIdMappings1_13.class, remap = false)
public abstract class MixinParticleIdMappings1_13 {

    @Shadow
    @Final
    private static List<?> particles;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void checkFootStepIdOverlap(CallbackInfo ci) {
        if (FootStepParticle1_12_2.RAW_ID < particles.size()) {
            throw new IllegalStateException("ViaVersion FootStepParticle ID overlaps with a vanilla 1.12.2 particle ID");
        }
    }

    @ModifyArg(method = "add(I)V", at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/protocols/v1_12_2to1_13/data/ParticleIdMappings1_13$NewParticle;<init>(ILcom/viaversion/viaversion/protocols/v1_12_2to1_13/data/ParticleIdMappings1_13$ParticleDataHandler;)V"))
    private static int replaceFootStepId(int id) {
        if (particles.size() == 28) { // minecraft:footstep -> viafabricplus:footstep
            return FootStepParticle1_12_2.RAW_ID;
        } else {
            return id;
        }
    }

}
