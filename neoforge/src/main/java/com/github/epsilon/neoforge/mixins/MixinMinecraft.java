package com.github.epsilon.neoforge.mixins;

import com.github.epsilon.neoforge.EpsilonNeoForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(GameConfig gameConfig, CallbackInfo ci) {
        EpsilonNeoForge.init();
    }

}
