package com.github.epsilon.graphics.shaders;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.LuminBindGroupLayouts;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.LuminRenderSystem.LuminRenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.Optional;

public class OverlayAntiAlias {

    public static final OverlayAntiAlias INSTANCE = new OverlayAntiAlias();

    private static final long MAX_TEXELS = 64L * 1024 * 1024;
    private static final RenderPipeline RESOLVE_4 = createPipeline(2);
    private static final RenderPipeline RESOLVE_16 = createPipeline(4);

    private LuminRenderTarget overlay;

    private OverlayAntiAlias() {
    }

    public void render(int requestedScale, Runnable drawAction) {
        RenderSystem.assertOnRenderThread();
        LuminRenderTarget previousTarget = LuminRenderSystem.getActiveTarget();
        GpuTextureView destination = LuminRenderSystem.resolveColorView();
        if (destination == null || destination.texture().getFormat() != GpuFormat.RGBA8_UNORM) {
            drawAction.run();
            return;
        }

        int width = destination.getWidth(0);
        int height = destination.getHeight(0);
        int maxSize = RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize();
        int scale = chooseScale(width, height, requestedScale, maxSize);
        if (scale == 1) {
            close();
            drawAction.run();
            return;
        }

        if (overlay == null) {
            overlay = LuminRenderTarget.create("-overlay-aa", width * scale, height * scale);
        } else {
            overlay.resize(width * scale, height * scale);
        }
        overlay.clear();

        LuminRenderSystem.setActiveTarget(overlay);
        try {
            drawAction.run();
        } finally {
            LuminRenderSystem.setActiveTarget(previousTarget);
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Epsilon Overlay AA Resolve", destination, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(scale == 4 ? RESOLVE_16 : RESOLVE_4));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InputSampler", overlay.colorView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }

    static int chooseScale(int width, int height, int requestedScale, int maxSize) {
        if (width <= 0 || height <= 0) return 1;
        int scale = requestedScale >= 4 ? 4 : 2;
        while (scale > 1 && ((long) width * scale > maxSize || (long) height * scale > maxSize || (long) width * height * scale * scale > MAX_TEXELS)) {
            scale /= 2;
        }
        return scale;
    }

    private static RenderPipeline createPipeline(int scale) {
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(ResourceLocationUtils.getIdentifier("pipeline/overlay_aa_" + scale))
                .withVertexShader("core/screenquad")
                .withFragmentShader(ResourceLocationUtils.getIdentifier("overlay_aa"))
                .withShaderDefine("SSAA_SCALE", scale)
                .withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                .withCull(false)
                .build();
    }

    public void close() {
        if (overlay != null) {
            overlay.close();
            overlay = null;
        }
    }

}
