package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.AspectRatio;
import com.mojang.blaze3d.ProjectionType;
import net.minecraft.client.renderer.Projection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projection.class)
public class MixinProjection {

    @Shadow
    private ProjectionType projectionType;

    @Shadow
    private float width;

    @Shadow
    private float height;

    @Inject(method = "getMatrix", at = @At("RETURN"))
    private void sakura$aspectRatio$getMatrix(Matrix4f dest, CallbackInfoReturnable<Matrix4f> cir) {
        if (!AspectRatio.INSTANCE.isEnabled() || projectionType != ProjectionType.PERSPECTIVE) return;

        float currentAspect = width / height;
        float scale = currentAspect / AspectRatio.INSTANCE.ratio.getValue().floatValue();

        Matrix4f m = cir.getReturnValue();
        m.m00(m.m00() * scale);
    }

}
