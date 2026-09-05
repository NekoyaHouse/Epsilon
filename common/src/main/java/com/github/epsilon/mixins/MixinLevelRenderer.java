package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.AfterRender3DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.shaders.CustomSkyShader;
import com.github.epsilon.modules.impl.render.CustomSky;
import com.github.epsilon.modules.impl.render.MotionBlur;
import com.github.epsilon.modules.impl.render.NoRender;
import com.github.epsilon.modules.impl.render.Shaders;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.objectweb.asm.Opcodes;
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

    @Inject(method = "render", at = @At("RETURN"))
    private void onPostRenderLevel(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline, CameraRenderState cameraState, Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, CallbackInfo ci) {
        MotionBlur.INSTANCE.captureFrame(cameraState, modelViewMatrix);
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelViewMatrix);
        EventBus.INSTANCE.post(new Render3DEvent(poseStack));
        EventBus.INSTANCE.post(new AfterRender3DEvent());
    }

    @Inject(method = "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("RETURN"), require = 0)
    private void addCustomSkyPass(FrameGraphBuilder frame, CameraRenderState cameraState, GpuBufferSlice skyFog, CallbackInfo ci) {
        if (CustomSky.INSTANCE.isEnabled()) {
            FramePass pass = frame.addPass("epsilon_custom_sky");
            ResourceHandle<RenderTarget> target = pass.readsAndWrites(this.targets.main);
            this.targets.main = target;
            pass.executes(() -> CustomSkyShader.INSTANCE.render(target.get(), CustomSky.INSTANCE));
        }
    }

    @Inject(method = "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Matrix4fc;)V", at = @At("RETURN"), require = 0)
    private void addCustomSkyPassNeoForge(FrameGraphBuilder frame, CameraRenderState cameraState, GpuBufferSlice skyFog, Matrix4fc modelViewMatrix, CallbackInfo ci) {
        if (CustomSky.INSTANCE.isEnabled()) {
            FramePass pass = frame.addPass("epsilon_custom_sky");
            ResourceHandle<RenderTarget> target = pass.readsAndWrites(this.targets.main);
            this.targets.main = target;
            pass.executes(() -> CustomSkyShader.INSTANCE.render(target.get(), CustomSky.INSTANCE));
        }
    }

    @ModifyExpressionValue(
            method = {
                    "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
                    "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Matrix4fc;)V" // For NeoForge
            },
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/CameraEntityRenderState;doesMobEffectBlockSky:Z", opcode = Opcodes.GETFIELD),
            require = 0
    )
    private boolean modifyMobEffectBlocksSky(boolean original) {
        if (NoRender.INSTANCE.isEnabled() && (NoRender.INSTANCE.blindness.getValue() || NoRender.INSTANCE.darkness.getValue())) {
            return false;
        }
        return original;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PostChain;addToFrame(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;IILnet/minecraft/client/renderer/PostChain$TargetBundle;)V", ordinal = 0))
    private void replaceEntityOutlineShader(PostChain instance, FrameGraphBuilder frame, int screenWidth, int screenHeight, PostChain.TargetBundle providedTargets, Operation<Void> original) {
        if (!Shaders.INSTANCE.isEnabled()) original.call(instance, frame, screenWidth, screenHeight, providedTargets);
    }

}
