package com.github.epsilon.mixins;

import com.github.epsilon.utils.network.ClientIdentityHider;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientBrandRetriever.class)
public class MixinClientBrandRetriever {

    @Inject(method = "getClientModName", at = @At("RETURN"), cancellable = true)
    private static void onGetClientModName(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(ClientIdentityHider.filterClientBrand(cir.getReturnValue()));
    }

}
