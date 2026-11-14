package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class MixinLevelExtractor {

    @Inject(method = "extract", at = @At("RETURN"))
    private void epsilon$hideWorldEffects(DeltaTracker deltaTracker, Camera camera, float partialTicks, CallbackInfo ci) {
        LevelRenderState state = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState;
        if (NoRender.INSTANCE.noWeather()) state.weatherRenderState.intensity = 0.0F;
        if (NoRender.INSTANCE.noWorldBorder()) state.worldBorderRenderState.alpha = 0.0F;
    }

    @Inject(method = "extractBlockDestroyAnimation", at = @At("HEAD"), cancellable = true)
    private void epsilon$hideBlockBreaking(Camera camera, LevelRenderState state, CallbackInfo ci) {
        if (NoRender.INSTANCE.noBlockBreakOverlay()) {
            state.blockBreakingRenderStates.clear();
            ci.cancel();
        }
    }

}
