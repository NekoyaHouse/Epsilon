package com.github.epsilon.fabric.mixins;

import com.github.epsilon.fabric.EpsilonFabric;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraftFabric {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInitializeClient(GameConfig gameConfig, CallbackInfo ci) {
        EpsilonFabric.init();
    }

}
