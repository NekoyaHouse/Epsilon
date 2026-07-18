package com.github.epsilon.holders;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.modules.impl.render.Shaders;
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
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * @author 06789
 * Thanks to furry client.
 */
public class ShaderHolder {

    public static final ShaderHolder INSTANCE = new ShaderHolder();

    private static final int PARAMS_UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec2()
            .putVec2()
            .putFloat()
            .putFloat()
            .putFloat()
            .putFloat()
            .putFloat()
            .putFloat()
            .putFloat()
            .putFloat()
            .putFloat()
            .putVec2()
            .get();

    private static final int COLOR_UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private RenderPipeline defaultPipeline;
    private RenderPipeline smokePipeline;
    private RenderPipeline gradientPipeline;
    private RenderPipeline snowPipeline;
    private RenderPipeline fadePipeline;
    private RenderPipeline copyPipeline;
    private RenderTarget shaderSwap;
    private RenderTarget handTarget;
    private RenderTarget chestTarget;
    private final OutlineBufferSource chestOutlineBufferSource = new OutlineBufferSource();
    private boolean renderingHands;
    private boolean capturedHands;
    private boolean renderingChests;
    private boolean capturedChests;
    private boolean preparedChests;

    public static final int EPSILON_CHEST_OUTLINE_MARKER = 0x01000001;

    private ShaderHolder() {
    }

    public void processEntityOutlineTarget(RenderTarget target, Shader shader) {
        if (target == null || target.width <= 0 || target.height <= 0 || target.getColorTextureView() == null) {
            return;
        }

        ensureProgram();
        ensureSwap(target.width, target.height);
        ShaderUniforms shaderUniforms = writeShaderUniforms(target.width, target.height);

        renderPass("epsilon_shader_effect", target, shaderSwap, pipeline(shader), shaderUniforms, usesColors(shader));
        // This fullscreen copy pass measured higher FPS than CommandEncoder.copyTextureToTexture on the tested renderer.
        renderPass("epsilon_shader_copy", shaderSwap, target, copyPipeline, null, false);
    }

    public void beginHandOutlineCapture(int width, int height) {
        if (Shaders.INSTANCE.shouldRenderHands()) {
            ensureHandTarget(Math.max(1, width), Math.max(1, height));
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorAndDepthTextures(handTarget.getColorTexture(), 0, handTarget.getDepthTexture(), 1.0);
            renderingHands = true;
            capturedHands = true;
        }
    }

    public void endHandOutlineCapture() {
        renderingHands = false;
    }

    public boolean isRenderingHands() {
        return renderingHands;
    }

    public RenderTarget getHandOutlineTarget() {
        return renderingHands ? handTarget : null;
    }

    public RenderTarget getChestOutlineTarget() {
        return renderingChests ? chestTarget : null;
    }

    public OutlineBufferSource getChestOutlineBufferSource() {
        if (!preparedChests) {
            RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
            prepareChestOutlineTarget(mainTarget);
            preparedChests = true;
        }
        capturedChests = true;
        return chestOutlineBufferSource;
    }

    public void endChestOutlineBatch() {
        if (!capturedChests || chestTarget == null) {
            return;
        }

        renderingChests = true;
        chestOutlineBufferSource.endOutlineBatch();
        renderingChests = false;
    }

    public void processHandOutlineTarget(RenderTarget mainTarget) {
        if (capturedHands) {
            capturedHands = false;

            if (!Shaders.INSTANCE.shouldRenderHands() || handTarget == null || mainTarget == null || mainTarget.getColorTextureView() == null) {
                return;
            }

            processEntityOutlineTarget(handTarget, Shaders.INSTANCE.handsMode.getValue());
            handTarget.blitAndBlendToTexture(mainTarget.getColorTextureView());
        }
    }

    public void prepareChestOutlineTarget(RenderTarget referenceTarget) {
        if (referenceTarget == null || referenceTarget.width <= 0 || referenceTarget.height <= 0) {
            return;
        }

        ensureChestTarget(referenceTarget.width, referenceTarget.height);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorAndDepthTextures(chestTarget.getColorTexture(), 0, chestTarget.getDepthTexture(), 1.0);
    }

    public void processChestOutlineTarget(RenderTarget mainTarget) {
        if (capturedChests) {
            capturedChests = false;

            if (chestTarget == null || mainTarget == null || mainTarget.getColorTextureView() == null) {
                return;
            }

            processEntityOutlineTarget(chestTarget, Shaders.INSTANCE.chestMode.getValue());
            chestTarget.blitAndBlendToTexture(mainTarget.getColorTextureView());
        }
        preparedChests = false;
    }

    private void renderPass(String name, RenderTarget input, RenderTarget output, RenderPipeline pipeline, ShaderUniforms shaderUniforms, boolean bindColors) {
        if (input.getColorTextureView() == null || output.getColorTextureView() == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> name,
                output.getColorTextureView(),
                OptionalInt.empty()
        )) {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            if (shaderUniforms != null) {
                renderPass.setUniform("ShaderParams", shaderUniforms.params());
                if (bindColors) {
                    renderPass.setUniform("ShaderColors", shaderUniforms.colors());
                }
            }
            renderPass.bindTexture("InputSampler", input.getColorTextureView(), sampler);
            renderPass.draw(0, 3);
        }
    }

    private ShaderUniforms writeShaderUniforms(int screenWidth, int screenHeight) {
        Shaders shaders = Shaders.INSTANCE;
        float width = Math.max(1.0f, screenWidth);
        float height = Math.max(1.0f, screenHeight);
        float scaledWidth = Math.max(1.0f, Minecraft.getInstance().getWindow().getGuiScaledWidth());
        float scaledHeight = Math.max(1.0f, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        Color outline = shaders.outlineColor.getValue();
        Color smokeOutline1 = shaders.smokeOutlineColor1.getValue();
        Color smokeOutline2 = shaders.smokeOutlineColor2.getValue();
        Color fill = shaders.fillColor1.getValue();
        Color smokeFill1 = shaders.fillColor2.getValue();
        Color smokeFill2 = shaders.fillColor3.getValue();

        return new ShaderUniforms(
                LuminRenderSystem.writeDynamicUniform(
                        "shader_params",
                        "Epsilon Shader Params UBO",
                        PARAMS_UNIFORMS_SIZE,
                        8,
                        new ShaderParams(
                                width,
                                height,
                                shaders.quality.getValue(),
                                shaders.lineWidth.getValue(),
                                shaders.smokeGlow.getValue() ? -1.0f : alpha(outline),
                                shaders.fillAlpha.getValue() / 255.0f,
                                shaders.alpha2.getValue() / 255.0f,
                                (Util.getMillis() % 100_000L) / 1000.0f,
                                shaders.factor.getValue().floatValue(),
                                shaders.gradient.getValue().floatValue(),
                                shaders.octaves.getValue(),
                                scaledWidth,
                                scaledHeight
                        )
                ),
                LuminRenderSystem.writeDynamicUniform(
                        "shader_colors",
                        "Epsilon Shader Colors UBO",
                        COLOR_UNIFORMS_SIZE,
                        8,
                        new ShaderColors(outline, smokeOutline1, smokeOutline2, fill, smokeFill1, smokeFill2)
                )
        );
    }

    private void ensureProgram() {
        if (defaultPipeline == null) {
            defaultPipeline = pipeline("outline", true);
            smokePipeline = pipeline("smoke", true);
            gradientPipeline = pipeline("gradient", false);
            snowPipeline = pipeline("snow", true);
            fadePipeline = pipeline("fade", true);
            copyPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(ResourceLocationUtils.getIdentifier("pipelines/shader_copy"))
                    .withVertexShader(("core/screenquad"))
                    .withFragmentShader(ResourceLocationUtils.getIdentifier("shader_copy"))
                    .withSampler("InputSampler")
                    .withCull(false)
                    .build();
        }
    }

    private void ensureSwap(int width, int height) {
        if (shaderSwap == null) {
            shaderSwap = new TextureTarget("Epsilon Shader Swap", width, height, false);
        }

        if (shaderSwap.width != width || shaderSwap.height != height) {
            shaderSwap.resize(width, height);
        }
    }

    private void ensureHandTarget(int width, int height) {
        if (handTarget == null) {
            handTarget = new TextureTarget("Epsilon Shader Hands", width, height, true);
        }

        if (handTarget.width != width || handTarget.height != height) {
            handTarget.resize(width, height);
        }
    }

    private void ensureChestTarget(int width, int height) {
        if (chestTarget == null) {
            chestTarget = new TextureTarget("Epsilon Shader Chests", width, height, true);
        }

        if (chestTarget.width != width || chestTarget.height != height) {
            chestTarget.resize(width, height);
        }
    }

    private RenderPipeline pipeline(String shader, boolean useColors) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(ResourceLocationUtils.getIdentifier("pipelines/shader_" + shader))
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(ResourceLocationUtils.getIdentifier("shader_" + shader))
                .withCull(false);

        builder.withUniform("ShaderParams", UniformType.UNIFORM_BUFFER);
        if (useColors) {
            builder.withUniform("ShaderColors", UniformType.UNIFORM_BUFFER);
        }

        return builder.withSampler("InputSampler")
                .build();
    }

    private RenderPipeline pipeline(Shader shader) {
        return switch (shader) {
            case Smoke -> smokePipeline;
            case Gradient -> gradientPipeline;
            case Snow -> snowPipeline;
            case Fade -> fadePipeline;
            default -> defaultPipeline;
        };
    }

    private boolean usesColors(Shader shader) {
        return shader != Shader.Gradient;
    }

    private static float red(Color color) {
        return color.getRed() / 255.0f;
    }

    private static float green(Color color) {
        return color.getGreen() / 255.0f;
    }

    private static float blue(Color color) {
        return color.getBlue() / 255.0f;
    }

    private static float alpha(Color color) {
        return color.getAlpha() / 255.0f;
    }

    public enum Shader {
        Default,
        Smoke,
        Gradient,
        Snow,
        Fade
    }

    private record ShaderUniforms(
            GpuBufferSlice params,
            GpuBufferSlice colors
    ) {
    }

    private record ShaderParams(
            float width,
            float height,
            float quality,
            float lineWidth,
            float outlineAlpha,
            float fillAlpha,
            float gradientAlpha,
            float time,
            float gradientFactor,
            float gradientScale,
            float octaves,
            float resolutionWidth,
            float resolutionHeight
    ) implements DynamicUniformStorage.DynamicUniform {

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putVec2(width, height)
                    .putVec2(1.0f / width, 1.0f / height)
                    .putFloat(quality)
                    .putFloat(lineWidth)
                    .putFloat(outlineAlpha)
                    .putFloat(fillAlpha)
                    .putFloat(gradientAlpha)
                    .putFloat(time)
                    .putFloat(gradientFactor)
                    .putFloat(gradientScale)
                    .putFloat(octaves)
                    .putVec2(resolutionWidth, resolutionHeight);
        }

    }

    private record ShaderColors(
            Color outline,
            Color smokeOutline1,
            Color smokeOutline2,
            Color fill,
            Color smokeFill1,
            Color smokeFill2
    ) implements DynamicUniformStorage.DynamicUniform {

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putVec4(red(outline), green(outline), blue(outline), alpha(outline))
                    .putVec4(red(smokeOutline1), green(smokeOutline1), blue(smokeOutline1), alpha(smokeOutline1))
                    .putVec4(red(smokeOutline2), green(smokeOutline2), blue(smokeOutline2), alpha(smokeOutline2))
                    .putVec4(red(fill), green(fill), blue(fill), alpha(fill))
                    .putVec4(red(smokeFill1), green(smokeFill1), blue(smokeFill1), alpha(smokeFill1))
                    .putVec4(red(smokeFill2), green(smokeFill2), blue(smokeFill2), alpha(smokeFill2));
        }

    }

}
