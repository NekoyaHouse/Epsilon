package com.github.epsilon.graphics.shaders;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.OptionalInt;

import static com.github.epsilon.Constants.mc;

public class FilterShader {

    public static final FilterShader INSTANCE = new FilterShader();

    private static final int UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec4()
            .get();

    private RenderPipeline pipeline;
    private RenderTarget input;

    private void ensureProgram() {
        if (this.pipeline == null) {
            this.pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(ResourceLocationUtils.getIdentifier("pipeline/filter"))
                    .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                    .withFragmentShader(ResourceLocationUtils.getIdentifier("filter"))
                    .withUniform("FilterColor", UniformType.UNIFORM_BUFFER)
                    .withSampler("InputSampler")
                    .withCull(false)
                    .build();
        }
    }

    private void ensureInput(RenderTarget framebuffer) {
        int fbWidth = framebuffer.width;
        int fbHeight = framebuffer.height;

        if (this.input == null) {
            this.input = new TextureTarget("Epsilon Filter Input", fbWidth, fbHeight, false);
        }

        if (this.input.width != fbWidth || this.input.height != fbHeight) {
            this.input.resize(fbWidth, fbHeight);
        }
    }

    public void renderToMainTarget(Color color) {
        render(mc.getMainRenderTarget(), color);
    }

    public void render(RenderTarget framebuffer, Color color) {
        this.ensureProgram();

        if (framebuffer == null || color == null || framebuffer.width <= 0 || framebuffer.height <= 0) {
            return;
        }

        if (framebuffer.getColorTexture() == null || framebuffer.getColorTextureView() == null) {
            return;
        }

        this.ensureInput(framebuffer);

        if (this.input.getColorTexture() == null || this.input.getColorTextureView() == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                framebuffer.getColorTexture(),
                this.input.getColorTexture(),
                0, 0, 0, 0, 0,
                framebuffer.width, framebuffer.height
        );

        GpuBufferSlice filterColor = LuminRenderSystem.writeDynamicUniform(
                "filter_color",
                "Epsilon Filter UBO",
                UNIFORMS_SIZE,
                4,
                new FilterColor(color)
        );

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "Epsilon Filter",
                framebuffer.getColorTextureView(),
                OptionalInt.empty()
        )) {
            renderPass.setPipeline(this.pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("FilterColor", filterColor);
            renderPass.bindTexture("InputSampler", this.input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderPass.draw(0, 3);
        }
    }

    private record FilterColor(float red, float green, float blue,
                               float alpha) implements DynamicUniformStorage.DynamicUniform {

        private FilterColor(Color color) {
            this(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
        }

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putVec4(red, green, blue, alpha);
        }

    }

}
