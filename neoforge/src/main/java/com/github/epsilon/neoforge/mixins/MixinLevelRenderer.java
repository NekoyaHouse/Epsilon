package com.github.epsilon.neoforge.mixins;

import com.github.epsilon.graphics.shaders.CustomSkyShader;
import com.github.epsilon.modules.impl.render.CustomSky;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Shadow
    @Final
    private LevelTargetBundle targets;

    @Inject(method = "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Matrix4fc;)V", at = @At("RETURN"))
    private void addCustomSkyPass(FrameGraphBuilder frame, CameraRenderState cameraState, GpuBufferSlice skyFog, Matrix4fc modelViewMatrix, CallbackInfo ci) {
        if (CustomSky.INSTANCE.isEnabled()) {
            FramePass pass = frame.addPass("epsilon_custom_sky");
            ResourceHandle<RenderTarget> target = pass.readsAndWrites(this.targets.main);
            this.targets.main = target;
            pass.executes(() -> CustomSkyShader.INSTANCE.render(target.get(), CustomSky.INSTANCE));
        }
    }

}
