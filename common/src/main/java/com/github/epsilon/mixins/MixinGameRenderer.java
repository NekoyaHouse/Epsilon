package com.github.epsilon.mixins;

import com.github.epsilon.managers.ShaderManager;
import com.github.epsilon.modules.impl.render.CameraClip;
import com.github.epsilon.modules.impl.render.FreeCamera;
import com.github.epsilon.modules.impl.render.Shaders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.phys.Vec3;
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

    @Final
    @Shadow
    private GlobalSettingsUniform globalSettingsUniform;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initializeGlobalSettingsUniform(Minecraft minecraft, FirstPersonHandsAndItemsRenderer firstPersonHandsAndItemsRenderer, ModelManager modelManager, ItemModelResolver itemModelResolver, CallbackInfo ci) {
        this.globalSettingsUniform.update(
                minecraft.getWindow().getWidth(),
                minecraft.getWindow().getHeight(),
                0.0,
                0L,
                0.0F,
                0,
                Vec3.ZERO,
                false
        );
    }

    @WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;close()V", ordinal = 0))
    private void renderHandOutlineBeforeFrameClose(FeatureRenderDispatcher.PreparedFrame frame, Operation<Void> original) {
        Shaders shaders = Shaders.INSTANCE;
        if (shaders.isEnabled() && shaders.getHands()) {
            try {
                ShaderManager.INSTANCE.renderHandOutline(frame);
            } finally {
                ShaderManager.INSTANCE.endHandOutlineCapture();
            }
        }
        original.call(frame);
    }

    @Inject(method = "render3dHud", at = @At("RETURN"))
    private void processShadersOutline(CameraRenderState cameraState, PlayerRenderState playerState, OptionsRenderState optionsState, boolean consistentDepthRequired, CallbackInfo ci) {
        Shaders shaders = Shaders.INSTANCE;
        if (shaders.isEnabled()) {
            ShaderManager.INSTANCE.processHandOutlineTarget(minecraft.gameRenderer.mainRenderTarget());
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void renderItemInHand(CameraRenderState cameraState, PlayerRenderState playerState, GpuTextureView depthTextureView, CallbackInfo ci) {
        if (!FreeCamera.INSTANCE.renderHands()) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "bobView", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void hookBobView(PoseStack instance, float xo, float yo, float zo, Operation<Void> original) {
        CameraClip cameraClip = CameraClip.INSTANCE;
        if (!cameraClip.isEnabled() || !cameraClip.getBetterBobView()) original.call(instance, xo, yo, zo);
    }

}
