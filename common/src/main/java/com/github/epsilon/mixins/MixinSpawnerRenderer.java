package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import net.minecraft.client.renderer.blockentity.SpawnerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpawnerRenderer.class)
public abstract class MixinSpawnerRenderer {

    @Inject(method = "submitEntityInSpawner", at = @At("HEAD"), cancellable = true)
    private static void epsilon$hideSpawnerEntity(CallbackInfo ci) {
        if (NoRender.INSTANCE.noMobInSpawner()) ci.cancel();
    }

}
