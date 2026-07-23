package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.SpawnerRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpawnerRenderer.class)
public abstract class MixinSpawnerRenderer {

    @Inject(method = "submitEntityInSpawner", at = @At("HEAD"), cancellable = true)
    private static void onRenderDisplayEntity(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, EntityRenderState displayEntity, EntityRenderDispatcher entityRenderer, float spin, float scale, CameraRenderState camera, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.noMobInSpawner.getValue()) ci.cancel();
    }

}
