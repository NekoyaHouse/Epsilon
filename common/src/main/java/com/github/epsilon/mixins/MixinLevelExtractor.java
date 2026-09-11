package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.client.renderer.state.level.WorldBorderRenderState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public class MixinLevelExtractor {

    @WrapWithCondition(
            method = "extract",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V")
    )
    private boolean extract$noWeather(WeatherEffectRenderer instance, ClientLevel level, float partialTicks, Vec3 cameraPos, WeatherRenderState renderState) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.weather.getValue()) {
            renderState.intensity = 0;
            return false;
        }
        return true;
    }

    @WrapWithCondition(
            method = "extract",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WorldBorderRenderer;extract(Lnet/minecraft/world/level/border/WorldBorder;FLnet/minecraft/world/phys/Vec3;DLnet/minecraft/client/renderer/state/level/WorldBorderRenderState;)V")
    )
    private boolean extract$noWorldBorder(WorldBorderRenderer instance, WorldBorder border, float deltaPartialTick, Vec3 cameraPos, double renderDistance, WorldBorderRenderState state) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.worldBorder.getValue()) {
            state.alpha = 0;
            return false;
        }
        return true;
    }

    @Inject(method = "extractBlockDestroyAnimation", at = @At("HEAD"), cancellable = true)
    private void onExtractBlockDestroyAnimation(Camera camera, LevelRenderState levelRenderState, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.blockBreakOverlay.getValue()) ci.cancel();
    }

}
