package com.github.epsilon.mixins;

import com.github.epsilon.holders.ShaderHolder;
import com.github.epsilon.modules.impl.render.Shaders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
public class MixinModelFeatureRenderer {

    @Unique
    private boolean epsilon$renderingChestOutline;

    @WrapOperation(method = "renderModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OutlineBufferSource;setColor(I)V"))
    private void redirectChestOutlineColor(OutlineBufferSource outlineBufferSource, int color, Operation<Void> original) {
        epsilon$renderingChestOutline = color == ShaderHolder.EPSILON_CHEST_OUTLINE_MARKER;
        if (epsilon$renderingChestOutline) {
            ShaderHolder.INSTANCE.getChestOutlineBufferSource().setColor(Shaders.INSTANCE.outlineColor.getValue().getRGB());
            return;
        }

        original.call(outlineBufferSource, color);
    }

    @WrapOperation(method = "renderModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OutlineBufferSource;getBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer redirectChestOutlineBuffer(OutlineBufferSource outlineBufferSource, RenderType renderType, Operation<VertexConsumer> original) {
        if (epsilon$renderingChestOutline) {
            return ShaderHolder.INSTANCE.getChestOutlineBufferSource().getBuffer(renderType);
        }

        return original.call(outlineBufferSource, renderType);
    }

    @Inject(method = "renderModel", at = @At("RETURN"))
    private <S> void clearChestOutlineState(SubmitNodeStorage.ModelSubmit<S> submit, RenderType renderType, VertexConsumer buffer, OutlineBufferSource outlineBufferSource, MultiBufferSource.BufferSource crumblingBufferSource, CallbackInfo ci) {
        if (epsilon$renderingChestOutline) {
            ShaderHolder.INSTANCE.endChestOutlineBatch();
        }
        epsilon$renderingChestOutline = false;
    }

}
