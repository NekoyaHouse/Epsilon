package com.github.epsilon.graphics.shaders;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.LuminBindGroupLayouts;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.DynamicGpuDataStorage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.util.Optional;

import static com.github.epsilon.Constants.mc;

/**
 * 整屏 FXAA 后处理，采样主渲染目标并写回，作为 {@code AntiAlias} 的 FXAA 模式使用。
 */
public class FXAAShader {

    public static final FXAAShader INSTANCE = new FXAAShader();

    private static final Identifier VERTEX_SHADER = Identifier.withDefaultNamespace("core/screenquad");
    private static final Identifier FRAGMENT_SHADER = ResourceLocationUtils.getIdentifier("fxaa");

    private static final int UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec4()
            .get();

    private RenderPipeline pipeline;
    private RenderTarget input;

    private FXAAShader() {
    }

    private void ensureProgram() {
        if (this.pipeline == null) {
            this.pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(ResourceLocationUtils.getIdentifier("pipeline/fxaa"))
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withBindGroupLayout(LuminBindGroupLayouts.FXAA_INFO)
                    .withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
                    .withCull(false)
                    .build();
        }
    }

    private void ensureInput(RenderTarget framebuffer) {
        int fbWidth = framebuffer.width;
        int fbHeight = framebuffer.height;

        if (this.input == null) {
            this.input = new TextureTarget("Epsilon FXAA Input", fbWidth, fbHeight, GpuFormat.RGBA8_UNORM, null);
        }

        if (this.input.width != fbWidth || this.input.height != fbHeight) {
            this.input.resize(fbWidth, fbHeight);
        }
    }

    public void renderMainTarget() {
        render(mc.gameRenderer.mainRenderTarget());
    }

    public void render(RenderTarget framebuffer) {
        this.ensureProgram();

        if (framebuffer == null || framebuffer.width <= 0 || framebuffer.height <= 0) {
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

        GpuBufferSlice fxaaInfo = LuminRenderSystem.writeDynamicUniform(
                "fxaa_info",
                "Epsilon FXAA UBO",
                UNIFORMS_SIZE,
                4,
                new FXAAInfo(framebuffer.width, framebuffer.height)
        );

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "Epsilon FXAA",
                framebuffer.getColorTextureView(),
                Optional.empty()
        )) {
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(this.pipeline));
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("FxaaInfo", fxaaInfo);
            renderPass.setUniform("InputSampler", this.input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderPass.draw(3, 1, 0, 0);
        }
    }

    /**
     * 释放 FXAA 的中间纹理；必须在渲染线程调用，重复调用安全。
     */
    public void close() {
        if (this.input != null) {
            this.input.destroyBuffers();
            this.input = null;
        }
    }

    private record FXAAInfo(float width, float height) implements DynamicGpuDataStorage.DynamicGpuData {

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putVec4(width, height, 1.0f / width, 1.0f / height);
        }

    }

}
