package me.sofurry.via.injection.mixin;

import me.sofurry.via.features.classic.cpe_extension.CPEAdditions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel_FeaturesClassicCpeExtension {

    @Inject(method = "getPrecipitationAt", at = @At(value = "HEAD"), cancellable = true)
    private void forceSnow(BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (CPEAdditions.isSnowing()) {
            cir.setReturnValue(Biome.Precipitation.SNOW);
        }
    }

}
