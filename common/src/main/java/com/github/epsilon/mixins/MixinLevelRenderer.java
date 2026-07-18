package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.AfterRender3DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.modules.impl.render.FreeCamera;
import com.github.epsilon.modules.impl.render.Shaders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;cullTerrain(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Z)V"))
    private boolean update$cullTerraion$modifySpectator(boolean spectator) {
        return FreeCamera.INSTANCE.isEnabled() || spectator;
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void onPostRenderLevel(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline, CameraRenderState cameraState, Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, ChunkSectionsToRender chunkSectionsToRender, CallbackInfo ci) {
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelViewMatrix);
        EventBus.INSTANCE.post(new Render3DEvent(poseStack));
        EventBus.INSTANCE.post(new AfterRender3DEvent());
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PostChain;addToFrame(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;IILnet/minecraft/client/renderer/PostChain$TargetBundle;)V", ordinal = 0))
    private void replaceEntityOutlineShader(PostChain instance, FrameGraphBuilder frame, int screenWidth, int screenHeight, PostChain.TargetBundle providedTargets, Operation<Void> original) {
        if (!Shaders.INSTANCE.isEnabled()) original.call(instance, frame, screenWidth, screenHeight, providedTargets);
    }

}
