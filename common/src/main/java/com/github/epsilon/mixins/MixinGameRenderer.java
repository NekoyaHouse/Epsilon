package com.github.epsilon.mixins;

import com.github.epsilon.holders.ShaderHolder;
import com.github.epsilon.modules.impl.render.FreeCamera;
import com.github.epsilon.modules.impl.render.Shaders;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Final
    @Shadow
    private Minecraft minecraft;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.BEFORE))
    private void processShadersOutline(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        Shaders shaders = Shaders.INSTANCE;
        if (shaders.isEnabled()) {
            RenderTarget target = minecraft.levelRenderer.entityOutlineTarget;
            ShaderHolder.INSTANCE.processEntityOutlineTarget(target, shaders.mode.getValue());
            ShaderHolder.INSTANCE.processChestOutlineTarget(minecraft.getMainRenderTarget());
            ShaderHolder.INSTANCE.processHandOutlineTarget(minecraft.getMainRenderTarget());
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void renderItemInHand(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix, CallbackInfo ci) {
        if (!FreeCamera.INSTANCE.renderHands()) {
            ci.cancel();
        }
    }

}
