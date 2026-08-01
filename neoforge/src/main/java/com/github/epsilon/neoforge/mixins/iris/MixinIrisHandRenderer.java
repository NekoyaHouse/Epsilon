package com.github.epsilon.neoforge.mixins.iris;

import com.github.epsilon.compat.IrisCompat;
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

    @Shadow
    @Final
    private RenderBuffers bufferSource;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void epsilon$registerIrisCompat(CallbackInfo ci) {
        IrisCompat.register(
                true,
                () -> HandRenderer.INSTANCE.isActive(),
                () -> ((MixinIrisHandRenderer) (Object) HandRenderer.INSTANCE).bufferSource.outlineBufferSource().endOutlineBatch()
        );
    }

}
