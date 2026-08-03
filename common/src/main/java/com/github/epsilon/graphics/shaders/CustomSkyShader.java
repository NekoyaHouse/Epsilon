package com.github.epsilon.graphics.shaders;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.LuminBindGroupLayouts;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.modules.impl.render.CustomSky;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Optional;

public class CustomSkyShader {

    public static final CustomSkyShader INSTANCE = new CustomSkyShader();

    private static final Identifier VERTEX_SHADER = Identifier.withDefaultNamespace("core/screenquad");
    private static final int UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private final EnumMap<CustomSky.ShaderMode, RenderPipeline> pipelines = new EnumMap<>(CustomSky.ShaderMode.class);
    private TextureTarget input;

    private CustomSkyShader() {
    }

    public void render(RenderTarget target, CustomSky module) {
        if (target == null || module == null || target.width <= 0 || target.height <= 0 || target.getColorTexture() == null || target.getColorTextureView() == null) {
            return;
        }

        CustomSky.ShaderMode mode = module.shader.getValue();
        RenderPipeline pipeline = getPipeline(mode);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        if (mode == CustomSky.ShaderMode.Local) {
            ensureInput(target);
            if (input == null || input.getColorTexture() == null || input.getColorTextureView() == null) {
                return;
            }

            encoder.copyTextureToTexture(
                    target.getColorTexture(), input.getColorTexture(),
                    0, 0, 0, 0, 0,
                    target.width, target.height
            );
        }

        Color color = module.color.getValue();
        Color backgroundColor = module.backgroundColor.getValue();
        float time = (Util.getMillis() % 100_000L) * module.speed.getValue().floatValue() * 0.0008f;
        GpuBufferSlice uniforms = LuminRenderSystem.writeDynamicUniform(
                "custom_sky",
                "Epsilon Custom Sky UBO",
                UNIFORMS_SIZE,
                16,
                new SkyUniforms(color, backgroundColor, target.width, target.height, time)
        );

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "Epsilon Custom Sky",
                target.getColorTextureView(),
                Optional.empty()
        )) {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("CustomSky", uniforms);
            if (mode == CustomSky.ShaderMode.Local) {
                renderPass.bindTexture("InputSampler", input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            }
            renderPass.draw(3, 1, 0, 0);
        }
    }

    private RenderPipeline getPipeline(CustomSky.ShaderMode mode) {
        return pipelines.computeIfAbsent(mode, shaderMode -> {
            RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(ResourceLocationUtils.getIdentifier("pipeline/custom_sky_" + shaderMode.name().toLowerCase()))
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(ResourceLocationUtils.getIdentifier("custom_sky_" + shaderMode.name().toLowerCase()))
                    .withBindGroupLayout(LuminBindGroupLayouts.CUSTOM_SKY)
                    .withCull(false);
            if (shaderMode == CustomSky.ShaderMode.Local) {
                builder.withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER);
            }
            return builder.build();
        });
    }

    private void ensureInput(RenderTarget target) {
        if (input == null) {
            input = new TextureTarget("Epsilon Custom Sky Input", target.width, target.height, false, GpuFormat.RGBA8_UNORM);
        } else if (input.width != target.width || input.height != target.height) {
            input.resize(target.width, target.height);
        }
    }

    private record SkyUniforms(
            float red, float green, float blue, float alpha,
            float backgroundRed, float backgroundGreen, float backgroundBlue, float backgroundAlpha,
            float width, float height, float time
    ) implements DynamicUniformStorage.DynamicUniform {
        private SkyUniforms(Color color, Color backgroundColor, int width, int height, float time) {
            this(
                    color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f,
                    backgroundColor.getRed() / 255.0f, backgroundColor.getGreen() / 255.0f,
                    backgroundColor.getBlue() / 255.0f, backgroundColor.getAlpha() / 255.0f,
                    width, height, time
            );
        }

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putVec4(red, green, blue, alpha)
                    .putVec4(backgroundRed, backgroundGreen, backgroundBlue, backgroundAlpha)
                    .putVec4(width, height, time, 0.0f);
        }
    }

}
