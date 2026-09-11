package com.github.epsilon.managers;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.LuminBindGroupLayouts;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.modules.impl.render.Shaders;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.joml.Vector4f;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Function;

import static com.github.epsilon.Constants.mc;

public class ShaderManager {

    public static final ShaderManager INSTANCE = new ShaderManager();

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
            .putFloat()
            .get();

    private static final int COLOR_UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static final int GLOW_UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec4()
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
    private RenderPipeline glowPipeline;
    private RenderPipeline glowMaskPipeline;
    private RenderPipeline copyPipeline;
    private RenderTarget shaderSwap;
    private RenderTarget glowSwap;
    private RenderTarget handTarget;
    private RenderTarget chestTarget;
    private final OutputTarget chestOutlineOutputTarget = new OutputTarget("epsilon_chest_outline", () -> chestTarget);
    private final Function<Identifier, RenderType> chestOutlineRenderTypes = Util.memoize(texture -> RenderType.create(
            "epsilon_chest_outline",
            RenderSetup.builder(RenderPipelines.OUTLINE_NO_CULL)
                    .withTexture("Sampler0", texture)
                    .setOutputTarget(chestOutlineOutputTarget)
                    .setOutline(RenderSetup.OutlineProperty.IS_OUTLINE)
                    .createRenderSetup()
    ));
    private boolean renderingHands;
    private boolean capturedHands;
    private boolean capturedChests;
    private boolean preparedChests;

    private ShaderManager() {
    }

    public void processOutlineTarget(RenderTarget target, Shaders.ShaderSettings settings) {
        if (target == null || target.width <= 0 || target.height <= 0 || target.getColorTextureView() == null) {
            return;
        }

        ensureProgram();
        boolean useGlow = settings.outlineMode.is(Shaders.OutlineMode.Glow);
        if (useGlow) {
            processGlowTarget(target, settings);
            return;
        }

        ensureSwap(target.width, target.height);
        Shader shader = settings.mode.getValue();
        ShaderUniforms shaderUniforms = writeShaderUniforms(target.width, target.height, settings, true, settings == Shaders.INSTANCE.entityShader);
        renderPass("epsilon_shader_effect", target, shaderSwap, pipeline(shader), shaderUniforms, true);
        renderPass("epsilon_shader_copy", shaderSwap, target, copyPipeline, null, false);
    }

    public void beginHandOutlineCapture(int width, int height) {
        if (Shaders.INSTANCE.hands.getValue()) {
            ensureHandTarget(Math.max(1, width), Math.max(1, height));
            if (!capturedHands) {
                CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
                encoder.clearColorAndDepthTextures(handTarget.getColorTexture(), new Vector4f(0.0f, 0.0f, 0.0f, 0.0f), handTarget.getDepthTexture(), 1.0);
            }
            renderingHands = true;
            capturedHands = true;
        }
    }

    public void endHandOutlineCapture() {
        if (renderingHands) {
            renderingHands = false;
        }
    }

    public RenderTarget getHandOutlineTarget() {
        return renderingHands ? handTarget : null;
    }

    public RenderTarget getChestOutlineTarget() {
        return chestTarget;
    }

    public RenderType prepareChestOutline(Identifier texture) {
        if (!preparedChests) {
            RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
            prepareChestOutlineTarget(mainTarget);
            preparedChests = true;
        }
        capturedChests = true;
        return chestOutlineRenderTypes.apply(texture);
    }

    public void processHandOutlineTarget(RenderTarget mainTarget) {
        if (capturedHands) {
            capturedHands = false;

            if (!Shaders.INSTANCE.hands.getValue() || handTarget == null || mainTarget == null || mainTarget.getColorTextureView() == null) {
                return;
            }

            processOutlineTarget(handTarget, Shaders.INSTANCE.handsShader);
            handTarget.blitAndBlendToTexture(mainTarget.getColorTextureView(), mainTarget.getDepthTextureView());
        }
    }

    public void prepareChestOutlineTarget(RenderTarget referenceTarget) {
        if (referenceTarget == null || referenceTarget.width <= 0 || referenceTarget.height <= 0) {
            return;
        }

        ensureChestTarget(referenceTarget.width, referenceTarget.height);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorAndDepthTextures(chestTarget.getColorTexture(), new Vector4f(0.0f, 0.0f, 0.0f, 0.0f), chestTarget.getDepthTexture(), 1.0);
    }

    public void processChestOutlineTarget(RenderTarget mainTarget) {
        if (capturedChests) {
            capturedChests = false;

            if (chestTarget == null || mainTarget == null || mainTarget.getColorTextureView() == null) {
                return;
            }

            processOutlineTarget(chestTarget, Shaders.INSTANCE.chestShader);
            chestTarget.blitAndBlendToTexture(mainTarget.getColorTextureView(), mainTarget.getDepthTextureView());
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
                Optional.empty()
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
            renderPass.draw(3, 1, 0, 0);
        }
    }

    private void processGlowTarget(RenderTarget target, Shaders.ShaderSettings settings) {
        ensureSwap(target.width, target.height);
        ensureGlowSwap(target.width, target.height);

        if (shaderSwap.getColorTexture() == null || glowSwap.getColorTexture() == null) {
            return;
        }

        GpuBufferSlice horizontalGlow = writeGlowUniforms(settings, target.width, target.height, 1.0f, 0.0f);
        GpuBufferSlice verticalGlow = writeGlowUniforms(settings, target.width, target.height, 0.0f, 1.0f);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                target.getColorTexture(),
                shaderSwap.getColorTexture(),
                0, 0, 0, 0, 0,
                target.width, target.height
        );

        renderGlowPass(
                encoder,
                shaderSwap,
                glowSwap,
                horizontalGlow
        );
        renderGlowPass(
                encoder,
                glowSwap,
                target,
                verticalGlow
        );
        renderMaskPass(encoder, shaderSwap, target);
    }

    private void renderGlowPass(CommandEncoder encoder, RenderTarget input, RenderTarget output, GpuBufferSlice glowUniforms) {
        if (input.getColorTextureView() == null || output.getColorTextureView() == null) {
            return;
        }

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "epsilon_shader_glow",
                output.getColorTextureView(),
                Optional.empty()
        )) {
            renderPass.setPipeline(glowPipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("GlowConfig", glowUniforms);
            renderPass.bindTexture("InputSampler", input.getColorTextureView(), sampler);
            renderPass.draw(3, 1, 0, 0);
        }
    }

    private void renderMaskPass(CommandEncoder encoder, RenderTarget input, RenderTarget output) {
        if (input.getColorTextureView() == null || output.getColorTextureView() == null) {
            return;
        }

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "epsilon_shader_glow_mask",
                output.getColorTextureView(),
                Optional.empty()
        )) {
            renderPass.setPipeline(glowMaskPipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("InputSampler", input.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            renderPass.draw(3, 1, 0, 0);
        }
    }

    private GpuBufferSlice writeGlowUniforms(Shaders.ShaderSettings settings, int targetWidth, int targetHeight, float directionX, float directionY) {
        boolean gradient = settings.glowColorMode.is(Shaders.GlowColorMode.Gradient);
        Color color = gradient ? settings.glowColor1.getValue() : settings.glowColor.getValue();
        return LuminRenderSystem.writeDynamicUniform(
                "shader_glow_config",
                "Epsilon Shader Glow Config UBO",
                GLOW_UNIFORMS_SIZE,
                8,
                new GlowParams(
                        targetWidth,
                        targetHeight,
                        mc.getWindow().getGuiScaledWidth(),
                        mc.getWindow().getGuiScaledHeight(),
                        color,
                        settings.glowColor1.getValue(),
                        settings.glowColor2.getValue(),
                        settings.glowRadius.getValue().floatValue(),
                        settings.glowExposure.getValue().floatValue(),
                        gradient ? settings.glowGradientSpeed.getValue().floatValue() : 0.0f,
                        gradient ? 1.0f : 0.0f,
                        (Util.getMillis() % 100_000L) / 1000.0f,
                        directionX,
                        directionY,
                        settings == Shaders.INSTANCE.entityShader ? 1.0f : 0.0f
                )
        );
    }

    private ShaderUniforms writeShaderUniforms(int screenWidth, int screenHeight, Shaders.ShaderSettings settings, boolean writeColors, boolean useTargetColors) {
        float width = Math.max(1.0f, screenWidth);
        float height = Math.max(1.0f, screenHeight);
        float scaledWidth = Math.max(1.0f, mc.getWindow().getGuiScaledWidth());
        float scaledHeight = Math.max(1.0f, mc.getWindow().getGuiScaledHeight());
        Color outline = settings.outlineColor.getValue();
        Color smokeOutline1 = settings.smokeOutlineColor1.getValue();
        Color smokeOutline2 = settings.smokeOutlineColor2.getValue();
        Color fill = settings.fillColor1.getValue();
        Color smokeFill1 = settings.fillColor2.getValue();
        Color smokeFill2 = settings.fillColor3.getValue();

        return new ShaderUniforms(
                LuminRenderSystem.writeDynamicUniform(
                        "shader_params",
                        "Epsilon Shader Params UBO",
                        PARAMS_UNIFORMS_SIZE,
                        8,
                        new ShaderParams(
                                width,
                                height,
                                settings.quality.getValue(),
                                settings.lineWidth.getValue(),
                                settings.glow.getValue() ? -1.0f : alpha(outline),
                                settings.fillAlpha.getValue() / 255.0f,
                                settings.gradientAlpha.getValue() / 255.0f,
                                (Util.getMillis() % 100_000L) / 1000.0f,
                                settings.factor.getValue().floatValue(),
                                settings.gradient.getValue().floatValue(),
                                settings.octaves.getValue(),
                                scaledWidth,
                                scaledHeight,
                                useTargetColors ? 1.0f : 0.0f
                        )
                ),
                writeColors ? LuminRenderSystem.writeDynamicUniform(
                        "shader_colors",
                        "Epsilon Shader Colors UBO",
                        COLOR_UNIFORMS_SIZE,
                        8,
                        new ShaderColors(outline, smokeOutline1, smokeOutline2, fill, smokeFill1, smokeFill2)
                ) : null
        );
    }

    private void ensureProgram() {
        if (defaultPipeline == null) {
            defaultPipeline = pipeline("outline", true);
            smokePipeline = pipeline("smoke", true);
            gradientPipeline = pipeline("gradient", true);
            snowPipeline = pipeline("snow", true);
            fadePipeline = pipeline("fade", true);
            glowPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(ResourceLocationUtils.getIdentifier("pipelines/shader_glow"))
                    .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                    .withFragmentShader(ResourceLocationUtils.getIdentifier("shader_glow"))
                    .withBindGroupLayout(LuminBindGroupLayouts.GLOW_CONFIG)
                    .withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
                    .withCull(false)
                    .build();
            glowMaskPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(ResourceLocationUtils.getIdentifier("pipelines/shader_glow_mask"))
                    .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                    .withFragmentShader(ResourceLocationUtils.getIdentifier("shader_glow_mask"))
                    .withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
                    .withColorTargetState(new ColorTargetState(
                            new BlendFunction(BlendFactor.ZERO, BlendFactor.ONE_MINUS_SRC_ALPHA)
                    ))
                    .withCull(false)
                    .build();
            copyPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(ResourceLocationUtils.getIdentifier("pipelines/shader_copy"))
                    .withVertexShader(("core/screenquad"))
                    .withFragmentShader(ResourceLocationUtils.getIdentifier("shader_copy"))
                    .withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
                    .withCull(false)
                    .build();
        }
    }

    private void ensureSwap(int width, int height) {
        if (shaderSwap == null) {
            shaderSwap = new TextureTarget("Epsilon Shader Swap", width, height, false, GpuFormat.RGBA8_UNORM);
        }

        if (shaderSwap.width != width || shaderSwap.height != height) {
            shaderSwap.resize(width, height);
        }
    }

    private void ensureGlowSwap(int width, int height) {
        if (glowSwap == null) {
            glowSwap = new TextureTarget("Epsilon Shader Glow Swap", width, height, false, GpuFormat.RGBA8_UNORM);
        }

        if (glowSwap.width != width || glowSwap.height != height) {
            glowSwap.resize(width, height);
        }
    }

    private void ensureHandTarget(int width, int height) {
        if (handTarget == null) {
            handTarget = new TextureTarget("Epsilon Shader Hands", width, height, true, GpuFormat.RGBA8_UNORM);
        }

        if (handTarget.width != width || handTarget.height != height) {
            handTarget.resize(width, height);
        }
    }

    private void ensureChestTarget(int width, int height) {
        if (chestTarget == null) {
            chestTarget = new TextureTarget("Epsilon Shader Chests", width, height, true, GpuFormat.RGBA8_UNORM);
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

        builder.withBindGroupLayout(LuminBindGroupLayouts.SHADER_PARAMS);
        if (useColors) {
            builder.withBindGroupLayout(LuminBindGroupLayouts.SHADER_COLORS);
        }

        return builder.withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
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
            float resolutionHeight,
            float useTargetColors
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
                    .putVec2(resolutionWidth, resolutionHeight)
                    .putFloat(useTargetColors);
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

    private record GlowParams(
            float targetWidth,
            float targetHeight,
            float resolutionWidth,
            float resolutionHeight,
            Color color,
            Color color1,
            Color color2,
            float radius,
            float exposure,
            float gradientSpeed,
            float gradient,
            float time,
            float directionX,
            float directionY,
            float useTargetColors
    ) implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putVec4(targetWidth, targetHeight, 0.0f, 0.0f)
                    .putVec4(resolutionWidth, resolutionHeight, 0.0f, 0.0f)
                    .putVec4(red(color), green(color), blue(color), alpha(color))
                    .putVec4(red(color1), green(color1), blue(color1), alpha(color1))
                    .putVec4(red(color2), green(color2), blue(color2), alpha(color2))
                    .putVec4(radius, exposure, gradientSpeed, gradient)
                    .putVec4(time, directionX, directionY, useTargetColors);
        }
    }

}
