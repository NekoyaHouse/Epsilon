package com.github.epsilon.graphics.shaders;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.LuminBindGroupLayouts;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.Optional;

import static com.github.epsilon.Constants.mc;

public class BlurShader {

    public static final BlurShader INSTANCE = new BlurShader();

    private static final int MAX_SEGMENTS = 64;

    private static final Identifier BLUR_PATH = ResourceLocationUtils.getIdentifier("blur");
    private static final Identifier BLUR_3D_BOX_PATH = ResourceLocationUtils.getIdentifier("blur_3d_box");

    private static final int UNIFORMS_SIZE = blurUniformsSize();

    private static final int BOX_UNIFORMS_SIZE = new Std140SizeCalculator().putVec4().get();

    private RenderPipeline pipeline;
    private RenderPipeline boxPipeline;
    private RenderTarget input;

    private void ensureProgram() {
        if (this.pipeline == null) {
            this.pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(ResourceLocationUtils.getIdentifier("pipeline/blur"))
                    .withVertexShader(BLUR_PATH)
                    .withFragmentShader(BLUR_PATH)
                    .withBindGroupLayout(LuminBindGroupLayouts.BLUR)
                    .withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .build();
        }
    }

    private void ensureBoxProgram() {
        if (this.boxPipeline == null) {
            this.boxPipeline = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(ResourceLocationUtils.getIdentifier("pipeline/blur_3d_box"))
                    .withVertexShader(BLUR_3D_BOX_PATH)
                    .withFragmentShader(BLUR_3D_BOX_PATH)
                    .withBindGroupLayout(LuminBindGroupLayouts.BOX_BLUR)
                    .withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withCull(false)
                    .build();
        }
    }

    public void render(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, float blurStrength) {
        render(null, x, y, width, height, rTL, rTR, rBR, rBL, blurStrength, null, null, 0);
    }

    public void render(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, float blurStrength, float[] segmentRects, float[] segmentRadii, int segmentCount) {
        render(null, x, y, width, height, rTL, rTR, rBR, rBL, blurStrength, segmentRects, segmentRadii, segmentCount);
    }

    public void render(LuminRenderSystem.LuminRenderTarget source, float x, float y, float width, float height, float radius, float blurStrength) {
        render(source, x, y, width, height, radius, radius, radius, radius, blurStrength, null, null, 0);
    }

    private void render(LuminRenderSystem.LuminRenderTarget source, float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, float blurStrength, float[] segmentRects, float[] segmentRadii, int segmentCount) {
        this.ensureProgram();

        if (width <= 0.0f || height <= 0.0f) {
            return;
        }

        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        LuminRenderSystem.LuminRenderTarget activeTarget = LuminRenderSystem.getActiveTarget();
        GpuTexture sourceTexture = source == null ? mainTarget.getColorTexture() : source.colorTexture();
        int sourceWidth = source == null ? mainTarget.width : source.width();
        int sourceHeight = source == null ? mainTarget.height : source.height();
        GpuTexture targetTexture = activeTarget == null ? mainTarget.getColorTexture() : activeTarget.colorTexture();
        GpuTextureView targetView = activeTarget == null ? mainTarget.getColorTextureView() : activeTarget.colorView();
        int targetWidth = activeTarget == null ? mainTarget.width : activeTarget.width();
        int targetHeight = activeTarget == null ? mainTarget.height : activeTarget.height();

        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0 || sourceTexture == null || targetTexture == null || targetView == null) {
            return;
        }

        if (input == null) {
            input = new TextureTarget("Lumin Blur Input", sourceWidth, sourceHeight, false, GpuFormat.RGBA8_UNORM);
        }

        if (this.input.width != sourceWidth || this.input.height != sourceHeight) {
            this.input.resize(sourceWidth, sourceHeight);
        }

        if (this.input.getColorTexture() == null || this.input.getColorTextureView() == null) {
            return;
        }

        float scale = (float) LuminRenderSystem.getGuiScale();
        float pxX = x * scale;
        float pxY = targetHeight - (y + height) * scale;
        float pxW = width * scale;
        float pxH = height * scale;

        float rTLPx = Math.max(0.0f, rTL * scale);
        float rTRPx = Math.max(0.0f, rTR * scale);
        float rBRPx = Math.max(0.0f, rBR * scale);
        float rBLPx = Math.max(0.0f, rBL * scale);

        float quality = Math.max(0.0f, blurStrength);
        int count = clampSegmentCount(segmentRects, segmentCount);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                sourceTexture,
                input.getColorTexture(),
                0, 0, 0, 0, 0,
                sourceWidth, sourceHeight
        );

        GpuBufferSlice blurUniforms = LuminRenderSystem.writeDynamicUniform(
                "blur_uniforms",
                "Lumin Blur UBO",
                UNIFORMS_SIZE,
                16,
                new BlurUniforms(
                        sourceWidth, sourceHeight, quality,
                        pxW, pxH, pxX, pxY,
                        rTLPx, rTRPx, rBRPx, rBLPx,
                        scale, targetHeight, segmentRects, segmentRadii, count
                )
        );

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "Lumin Blur",
                targetView,
                Optional.empty()
        )) {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("BlurUniforms", blurUniforms);
            renderPass.bindTexture("InputSampler", input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderPass.draw(3, 1, 0, 0);
        }
    }

    public void render(float x, float y, float width, float height, float radius, float blurStrength) {
        render(x, y, width, height, radius, radius, radius, radius, blurStrength);
    }

    public void render(float x, float y, float width, float height, float radius, float blurStrength, float[] segmentRects, float[] segmentRadii, int segmentCount) {
        render(x, y, width, height, radius, radius, radius, radius, blurStrength, segmentRects, segmentRadii, segmentCount);
    }

    public void render3DBox(AABB box, double blurStrength) {
        this.ensureBoxProgram();

        RenderTarget fb = mc.gameRenderer.mainRenderTarget();
        if (fb.width <= 0 || fb.height <= 0) {
            return;
        }

        if (fb.getColorTexture() == null || fb.getColorTextureView() == null) {
            return;
        }

        if (input == null) {
            input = new TextureTarget("Lumin Blur Input", fb.width, fb.height, false, GpuFormat.RGBA8_UNORM);
        }

        if (this.input.width != fb.width || this.input.height != fb.height) {
            this.input.resize(fb.width, fb.height);
        }

        if (this.input.getColorTexture() == null || this.input.getColorTextureView() == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                fb.getColorTexture(),
                input.getColorTexture(),
                0, 0, 0, 0, 0,
                fb.width, fb.height
        );

        float quality = Math.max(0.0f, (float) blurStrength);
        GpuBufferSlice boxBlurUniforms = LuminRenderSystem.writeDynamicUniform(
                "box_blur_uniforms",
                "Lumin 3D Box Blur UBO",
                BOX_UNIFORMS_SIZE,
                16,
                new BoxBlurUniforms(fb.width, fb.height, quality)
        );

        LuminImmediateRenderer.PosColorQuads renderer = LuminImmediateRenderer.beginPosColorQuads(this.boxPipeline, pass -> {
            pass.setUniform("BoxBlurUniforms", boxBlurUniforms);
            pass.bindTexture("InputSampler", input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        });
        addBoxVertices(renderer, box);
        renderer.end();
    }

    private void addBoxVertices(LuminImmediateRenderer.PosColorQuads renderer, AABB box) {
        Vec3 camPos = mc.getEntityRenderDispatcher().camera.position();

        float minX = (float) (box.minX - camPos.x);
        float minY = (float) (box.minY - camPos.y);
        float minZ = (float) (box.minZ - camPos.z);
        float maxX = (float) (box.maxX - camPos.x);
        float maxY = (float) (box.maxY - camPos.y);
        float maxZ = (float) (box.maxZ - camPos.z);

        Matrix4f matrix = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix;

        vertex(renderer, matrix, minX, minY, minZ);
        vertex(renderer, matrix, minX, minY, maxZ);
        vertex(renderer, matrix, maxX, minY, maxZ);
        vertex(renderer, matrix, maxX, minY, minZ);

        vertex(renderer, matrix, minX, maxY, minZ);
        vertex(renderer, matrix, maxX, maxY, minZ);
        vertex(renderer, matrix, maxX, maxY, maxZ);
        vertex(renderer, matrix, minX, maxY, maxZ);

        vertex(renderer, matrix, minX, minY, minZ);
        vertex(renderer, matrix, minX, maxY, minZ);
        vertex(renderer, matrix, maxX, maxY, minZ);
        vertex(renderer, matrix, maxX, minY, minZ);

        vertex(renderer, matrix, maxX, minY, minZ);
        vertex(renderer, matrix, maxX, maxY, minZ);
        vertex(renderer, matrix, maxX, maxY, maxZ);
        vertex(renderer, matrix, maxX, minY, maxZ);

        vertex(renderer, matrix, minX, minY, maxZ);
        vertex(renderer, matrix, maxX, minY, maxZ);
        vertex(renderer, matrix, maxX, maxY, maxZ);
        vertex(renderer, matrix, minX, maxY, maxZ);

        vertex(renderer, matrix, minX, minY, minZ);
        vertex(renderer, matrix, minX, minY, maxZ);
        vertex(renderer, matrix, minX, maxY, maxZ);
        vertex(renderer, matrix, minX, maxY, minZ);
    }

    private void vertex(LuminImmediateRenderer.PosColorQuads renderer, Matrix4f matrix, float x, float y, float z) {
        renderer.vertex(matrix, x, y, z, -1);
    }

    private static int blurUniformsSize() {
        Std140SizeCalculator calculator = new Std140SizeCalculator().putVec3().putVec4().putVec4().putVec4();
        for (int i = 0; i < MAX_SEGMENTS * 2; i++) {
            calculator.putVec4();
        }
        return calculator.get();
    }

    private static int clampSegmentCount(float[] segmentRects, int segmentCount) {
        if (segmentRects == null || segmentCount <= 0) return 0;
        return Math.min(MAX_SEGMENTS, Math.min(segmentCount, segmentRects.length / 4));
    }

    private record BlurUniforms(
            float width,
            float height,
            float quality,
            float rectWidth,
            float rectHeight,
            float rectX,
            float rectY,
            float radiusTopLeft,
            float radiusTopRight,
            float radiusBottomRight,
            float radiusBottomLeft,
            float scale,
            float targetHeight,
            float[] segmentRects,
            float[] segmentRadii,
            int segmentCount
    ) implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder builder = Std140Builder.intoBuffer(buffer)
                    .putVec3(width, height, quality)
                    .putVec4(rectWidth, rectHeight, rectX, rectY)
                    .putVec4(radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft)
                    .putVec4(segmentCount, 0.0f, 0.0f, 0.0f);

            for (int i = 0; i < MAX_SEGMENTS; i++) {
                if (i < segmentCount) {
                    int offset = i * 4;
                    float segmentX = segmentRects[offset];
                    float segmentY = segmentRects[offset + 1];
                    float segmentWidth = segmentRects[offset + 2];
                    float segmentHeight = segmentRects[offset + 3];
                    builder.putVec4(segmentX * scale, targetHeight - (segmentY + segmentHeight) * scale, segmentWidth * scale, segmentHeight * scale);
                } else {
                    builder.putVec4(0.0f, 0.0f, 0.0f, 0.0f);
                }
            }

            for (int i = 0; i < MAX_SEGMENTS; i++) {
                float radius = segmentRadii != null && i < segmentCount && i < segmentRadii.length
                        ? Math.max(0.0f, segmentRadii[i] * scale)
                        : 0.0f;
                builder.putVec4(radius, 0.0f, 0.0f, 0.0f);
            }
        }
    }

    private record BoxBlurUniforms(
            float width, float height, float quality
    ) implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putVec4(width, height, quality, 0.0f);
        }
    }

}
