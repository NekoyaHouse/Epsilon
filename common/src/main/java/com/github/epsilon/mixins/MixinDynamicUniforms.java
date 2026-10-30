package com.github.epsilon.mixins;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import net.minecraft.client.renderer.DynamicGpuData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DynamicGpuData.class)
public class MixinDynamicUniforms {

    @Inject(method = "reset", at = @At("RETURN"))
    private void onReset(CallbackInfo ci) {
        LuminRenderSystem.INSTANCE.endDynamicUniformFrame();
        LuminImmediateRenderer.INSTANCE.endFrame();
        LuminRenderSystem.INSTANCE.beginRenderFrame();
        TtfFontLoader.Companion.beginRenderFrame();
    }

}
