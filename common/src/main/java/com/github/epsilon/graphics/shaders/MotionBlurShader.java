package com.github.epsilon.graphics.shaders;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.LuminBindGroupLayouts;
import com.github.epsilon.graphics.LuminRenderSystem;
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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.ByteBuffer;
import java.util.Optional;

import static com.github.epsilon.Constants.mc;

public class MotionBlurShader {

    public static final MotionBlurShader INSTANCE = new MotionBlurShader();

    private static final int MAX_SAMPLES = 256;
    private static final double MAX_FRAME_TIME_SECONDS = 0.25;
    private static final double MAX_CAMERA_DELTA_SQUARED = 1024.0;
    private static final Identifier VERTEX_SHADER = Identifier.withDefaultNamespace("core/screenquad");
    private static final Identifier FRAGMENT_SHADER = ResourceLocationUtils.getIdentifier("motion_blur");
    private static final int UNIFORMS_SIZE = new Std140SizeCalculator()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putVec4()
            .putVec4()
            .putIVec4()
            .get();

    private final Matrix4f previousView = new Matrix4f();
    private final Matrix4f previousProjection = new Matrix4f();
    private Vec3 previousCameraPosition = Vec3.ZERO;
    private RenderPipeline pipeline;
    private RenderTarget input;
    private long previousFrameNanos;
    private int previousWidth;
    private int previousHeight;
    private boolean hasHistory;

    private MotionBlurShader() {
    }

    public void render(RenderTarget target, Matrix4fc view, Matrix4fc projection, Vec3 cameraPosition, Settings settings) {
        long now = System.nanoTime();
        double frameTime = previousFrameNanos == 0L ? 0.0 : (now - previousFrameNanos) / 1_000_000_000.0;
        Vec3 cameraDelta = cameraPosition.subtract(previousCameraPosition);
        boolean targetValid = target != null
                && target.width > 0
                && target.height > 0
                && target.getColorTexture() != null
                && target.getColorTextureView() != null
                && target.getDepthTextureView() != null;
        boolean historyValid = hasHistory
                && targetValid
                && target.width == previousWidth
                && target.height == previousHeight
                && frameTime > 0.0
                && frameTime <= MAX_FRAME_TIME_SECONDS
                && cameraDelta.lengthSqr() <= MAX_CAMERA_DELTA_SQUARED;

        if (historyValid && settings.render() && settings.strength() > 0.0f) {
            renderEffect(target, view, projection, cameraDelta, settings, frameTime);
        }

        previousView.set(view);
        previousProjection.set(projection);
        previousCameraPosition = cameraPosition;
        previousFrameNanos = now;
        if (targetValid) {
            previousWidth = target.width;
            previousHeight = target.height;
            hasHistory = true;
        } else {
            hasHistory = false;
        }
    }

    private void renderEffect(RenderTarget target, Matrix4fc view, Matrix4fc projection, Vec3 cameraDelta, Settings settings, double frameTime) {
        ensureProgram();
        ensureInput(target.width, target.height);
        if (input.getColorTexture() == null || input.getColorTextureView() == null) return;

        float frameScale = 1.0f;
        if (settings.refreshRateScaling()) {
            int refreshRate = mc.getWindow().getRefreshRate();
            if (refreshRate > 0) {
                frameScale = Math.max(1.0f, (float) (1.0 / frameTime) / refreshRate);
            }
        }

        float scaledStrength = settings.strength() * frameScale;
        int scaledSamples = Mth.clamp(Math.round(settings.samples() * frameScale), 4, MAX_SAMPLES);
        MotionBlurUniforms uniformData = new MotionBlurUniforms(
                new Matrix4f(view).invert(),
                new Matrix4f(projection).invert(),
                previousView,
                previousProjection,
                cameraDelta,
                target.width,
                target.height,
                scaledStrength,
                RenderSystem.getDevice().getDeviceInfo().isZZeroToOne(),
                scaledSamples,
                settings.algorithm(),
                settings.depthBlur()
        );
        GpuBufferSlice uniforms = LuminRenderSystem.writeDynamicUniform(
                "motion_blur_data",
                "Epsilon Motion Blur UBO",
                UNIFORMS_SIZE,
                4,
                uniformData
        );

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                target.getColorTexture(),
                input.getColorTexture(),
                0, 0, 0, 0, 0,
                target.width, target.height
        );

        try (RenderPass pass = encoder.createRenderPass(
                () -> "Epsilon Motion Blur",
                target.getColorTextureView(),
                Optional.empty()
        )) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("MotionBlurData", uniforms);
            pass.bindTexture("InputSampler", input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.bindTexture("DepthSampler", target.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
    }

    private void ensureProgram() {
        if (pipeline != null) return;

        pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(ResourceLocationUtils.getIdentifier("pipeline/motion_blur"))
                .withVertexShader(VERTEX_SHADER)
                .withFragmentShader(FRAGMENT_SHADER)
                .withBindGroupLayout(LuminBindGroupLayouts.MOTION_BLUR_DATA)
                .withBindGroupLayout(LuminBindGroupLayouts.MOTION_BLUR_TEXTURES)
                .withCull(false)
                .build();
    }

    private void ensureInput(int width, int height) {
        if (input == null) {
            input = new TextureTarget("Epsilon Motion Blur Input", width, height, false, GpuFormat.RGBA8_UNORM);
        } else if (input.width != width || input.height != height) {
            input.resize(width, height);
        }
    }

    public void resetHistory() {
        hasHistory = false;
        previousFrameNanos = 0L;
    }

    public void close() {
        if (input != null) {
            input.destroyBuffers();
            input = null;
        }
        resetHistory();
    }

    public record Settings(
            float strength,
            int samples,
            int algorithm,
            boolean depthBlur,
            boolean refreshRateScaling,
            boolean render
    ) {
    }

    private record MotionBlurUniforms(
            Matrix4fc viewInverse,
            Matrix4fc projectionInverse,
            Matrix4fc previousView,
            Matrix4fc previousProjection,
            Vec3 cameraDelta,
            float width,
            float height,
            float strength,
            boolean zZeroToOne,
            int samples,
            int algorithm,
            boolean depthBlur
    ) implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putMat4f(viewInverse)
                    .putMat4f(projectionInverse)
                    .putMat4f(previousView)
                    .putMat4f(previousProjection)
                    .putVec4((float) cameraDelta.x, (float) cameraDelta.y, (float) cameraDelta.z, 0.0f)
                    .putVec4(width, height, strength, zZeroToOne ? 1.0f : 0.0f)
                    .putIVec4(samples, algorithm, depthBlur ? 1 : 0, 0);
        }
    }

}
