package com.github.epsilon.mixins;

import com.github.epsilon.managers.ShaderManager;
import com.github.epsilon.modules.impl.render.FreeCamera;
import com.github.epsilon.modules.impl.render.Shaders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
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

    @WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lnet/minecraft/client/renderer/SubmitNodeStorage;)V"))
    private void renderHandFeaturesWithOutline(FeatureRenderDispatcher dispatcher, SubmitNodeStorage storage, Operation<Void> original) {
        if (Shaders.INSTANCE.isEnabled() && Shaders.INSTANCE.hands.getValue()) {
            try (FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(storage)) {
                frame.executeSolid();
                frame.executeTranslucent();
                frame.executeOutline();
                frame.executeTranslucentAfterTerrain();
                frame.executeAlwaysOnTop();
            } finally {
                ShaderManager.INSTANCE.endHandOutlineCapture();
            }
        } else {
            original.call(dispatcher, storage);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.BEFORE))
    private void processShadersOutline(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        Shaders shaders = Shaders.INSTANCE;
        if (shaders.isEnabled()) {
            ShaderManager.INSTANCE.processOutlineTarget(minecraft.levelRenderer.entityOutlineTarget, shaders.entityShader);
            ShaderManager.INSTANCE.processChestOutlineTarget(minecraft.gameRenderer.mainRenderTarget());
            ShaderManager.INSTANCE.processHandOutlineTarget(minecraft.gameRenderer.mainRenderTarget());
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void renderItemInHand(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix, CallbackInfo ci) {
        if (!FreeCamera.INSTANCE.renderHands()) {
            ci.cancel();
        }
    }

}
