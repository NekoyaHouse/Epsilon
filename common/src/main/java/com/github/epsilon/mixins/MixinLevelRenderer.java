package com.github.epsilon.mixins;

import com.github.epsilon.events.impl.AfterRender3DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.shaders.CustomSkyShader;
import com.github.epsilon.managers.ShaderManager;
import com.github.epsilon.modules.impl.render.*;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
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

    @Shadow
    private boolean currentFrameRendersEntityOutline;

    @Inject(method = "render", at = @At("RETURN"))
    private void onPostRenderLevel(GraphicsResourceAllocator resourceAllocator, boolean renderOutline, CameraRenderState cameraState, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, boolean consistentDepthRequired, CallbackInfo ci) {
        Matrix4fc modelViewMatrix = cameraState.viewRotationMatrix;
        MotionBlur.INSTANCE.captureFrame(cameraState, modelViewMatrix);
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelViewMatrix);

        new Render3DEvent(poseStack).post();
        new AfterRender3DEvent().post();

        if (this.currentFrameRendersEntityOutline) {
            ShaderManager.INSTANCE.processEntityOutlineTarget(((LevelRenderer) (Object) this).entityOutlineTarget);
        }

        if (Shaders.INSTANCE.isEnabled()) {
            ShaderManager.INSTANCE.processChestOutlineTarget(modelViewMatrix);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderLevelStart(GraphicsResourceAllocator resourceAllocator, boolean renderOutline, CameraRenderState cameraState, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, boolean consistentDepthRequired, CallbackInfo ci) {
        Chams.INSTANCE.resetAlwaysOnTopSubmits();
    }

    @ModifyReturnValue(method = "frameHasAlwaysOnTopGizmos", at = @At("RETURN"))
    private boolean forceAlwaysOnTopPassForChams(boolean original) {
        return original || Chams.INSTANCE.hasAlwaysOnTopSubmits();
    }

    @Inject(method = "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;)V", at = @At("RETURN"), require = 0)
    private void addCustomSkyPass(FrameGraphBuilder frame, CameraRenderState cameraState, GpuBufferSlice skyFog, CallbackInfo ci) {
        if (CustomSky.INSTANCE.isEnabled()) {
            FramePass pass = frame.addPass("epsilon_custom_sky");
            ResourceHandle<RenderTarget> target = pass.readsAndWrites(this.targets.main);
            this.targets.main = target;
            pass.executes(() -> CustomSkyShader.INSTANCE.render(target.get(), CustomSky.INSTANCE));
        }
    }

    @Inject(method = "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Matrix4fc;)V", at = @At("RETURN"), require = 0)
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
                    "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;)V",
                    "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Matrix4fc;)V" // For NeoForge
            },
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/CameraEntityRenderState;doesMobEffectBlockSky:Z", opcode = Opcodes.GETFIELD),
            require = 0
    )
    private boolean modifyMobEffectBlocksSky(boolean original) {
        if (NoRender.INSTANCE.isEnabled() && (NoRender.INSTANCE.getBlindness() || NoRender.INSTANCE.getDarkness())) {
            return false;
        }
        return original;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PostChain;addToFrame(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;IILnet/minecraft/client/renderer/PostChain$TargetBundle;)V", ordinal = 0))
    private void replaceEntityOutlineShader(PostChain instance, FrameGraphBuilder frame, int screenWidth, int screenHeight, PostChain.TargetBundle providedTargets, Operation<Void> original) {
        if (!Shaders.INSTANCE.isEnabled()) original.call(instance, frame, screenWidth, screenHeight, providedTargets);
    }

}
