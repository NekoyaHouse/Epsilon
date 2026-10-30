package com.github.epsilon.fabric.mixins

import com.github.epsilon.fabric.EpsilonFabric
import net.minecraft.client.Minecraft
import net.minecraft.client.main.GameConfig
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Minecraft::class)
open class MixinMinecraftFabric {

    @Inject(method = ["<init>"], at = At("TAIL"))
    private fun onInitializeClient(gameConfig: GameConfig, ci: CallbackInfo) {
        EpsilonFabric.init()
    }

}
