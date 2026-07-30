package com.github.epsilon.fabric.mixins.iris;

import com.github.epsilon.holders.ShaderHolder;
import net.irisshaders.iris.pathways.HandRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HandRenderer.class, remap = false)
public abstract class MixinIrisHandRenderer {

    @Final
    @Shadow
    private RenderBuffers bufferSource;

    @Inject(method = "endRender", at = @At("RETURN"))
    private void flushHandOutline(CallbackInfo ci) {
        if (ShaderHolder.INSTANCE.renderingHands) {
            bufferSource.outlineBufferSource().endOutlineBatch();
            ShaderHolder.INSTANCE.customHandOutlineComplete = true;;
        }
    }

}
