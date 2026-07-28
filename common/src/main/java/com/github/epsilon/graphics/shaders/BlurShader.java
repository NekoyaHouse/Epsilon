package com.github.epsilon.graphics.shaders;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.utils.render.ScissorUtils;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static com.github.epsilon.Constants.mc;

public class BlurShader {

    public static final BlurShader INSTANCE = new BlurShader();

    private static final int MAX_SEGMENTS = 64;

    private static final Identifier BLUR_PATH = ResourceLocationUtils.getIdentifier("blur");
    private static final Identifier BLUR_3D_BOX_PATH = ResourceLocationUtils.getIdentifier("blur_3d_box");

    private static final int UNIFORMS_SIZE = blurUniformsSize();

    private static final int BOX_UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec4()
            .get();

    private RenderPipeline pipeline;
    private RenderPipeline boxPipeline;
    private RenderTarget input;

    private void ensureProgram() {
        if (this.pipeline == null) {
            this.pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(ResourceLocationUtils.getIdentifier("pipeline/blur"))
                    .withVertexShader(BLUR_PATH)
                    .withFragmentShader(BLUR_PATH)
                    .withUniform("BlurUniforms", UniformType.UNIFORM_BUFFER)
                    .withSampler("InputSampler")
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
                    .withUniform("BoxBlurUniforms", UniformType.UNIFORM_BUFFER)
                    .withSampler("InputSampler")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withCull(false)
                    .build();
        }
    }

    public void render(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, float blurStrength) {
        render(x, y, width, height, rTL, rTR, rBR, rBL, blurStrength, null, null, 0);
    }

    public void render(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, float blurStrength, float[] segmentRects, float[] segmentRadii, int segmentCount) {
        this.ensureProgram();

        if (width <= 0.0f || height <= 0.0f) {
            return;
        }

        RenderTarget sourceTarget = mc.getMainRenderTarget();
        LuminRenderSystem.LuminRenderTarget activeTarget = LuminRenderSystem.getActiveTarget();
        GpuTexture targetTexture = activeTarget == null ? sourceTarget.getColorTexture() : activeTarget.colorTexture();
        GpuTextureView targetView = activeTarget == null ? sourceTarget.getColorTextureView() : activeTarget.colorView();
        int targetWidth = activeTarget == null ? sourceTarget.width : activeTarget.width();
        int targetHeight = activeTarget == null ? sourceTarget.height : activeTarget.height();

        if (sourceTarget.width <= 0 || sourceTarget.height <= 0 || targetWidth <= 0 || targetHeight <= 0
                || sourceTarget.getColorTexture() == null || targetTexture == null || targetView == null) {
            return;
        }

        if (input == null) {
            input = new TextureTarget("Lumin Blur Input", sourceTarget.width, sourceTarget.height, false);
        }

        if (this.input.width != sourceTarget.width || this.input.height != sourceTarget.height) {
            this.input.resize(sourceTarget.width, sourceTarget.height);
        }

        if (this.input.getColorTexture() == null || this.input.getColorTextureView() == null) {
            return;
        }

        LuminRenderSystem.ScissorRect scissor = LuminRenderSystem.toFramebufferScissor(x, y, width, height);
        if (!ScissorUtils.isVisible(scissor)) {
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
                sourceTarget.getColorTexture(),
                input.getColorTexture(),
                0, 0, 0, 0, 0,
                sourceTarget.width, sourceTarget.height
        );

        GpuBufferSlice blurUniforms = LuminRenderSystem.writeDynamicUniform(
                "blur_uniforms",
                "Lumin Blur UBO",
                UNIFORMS_SIZE,
                16,
                new BlurUniforms(
                        sourceTarget.width, sourceTarget.height, quality,
                        pxW, pxH, pxX, pxY,
                        rTLPx, rTRPx, rBRPx, rBLPx,
                        scale, targetHeight, segmentRects, segmentRadii, count
                )
        );

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "Lumin Blur",
                targetView,
                OptionalInt.empty()
        )) {
            renderPass.setPipeline(pipeline);
            ScissorUtils.enableScissor(renderPass, scissor);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("BlurUniforms", blurUniforms);
            renderPass.bindTexture("InputSampler", input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderPass.draw(0, 3);
        }
    }

    public void render(float x, float y, float width, float height, float radius, float blurStrength) {
        render(x, y, width, height, radius, radius, radius, radius, blurStrength);
    }

    public void render(float x, float y, float width, float height, float radius, float blurStrength,
                       float[] segmentRects, float[] segmentRadii, int segmentCount) {
        render(x, y, width, height, radius, radius, radius, radius, blurStrength, segmentRects, segmentRadii, segmentCount);
    }

    public void render3DBox(AABB box, double blurStrength) {
        this.ensureBoxProgram();

        RenderTarget fb = mc.getMainRenderTarget();
        if (fb.width <= 0 || fb.height <= 0) {
            return;
        }

        if (fb.getColorTexture() == null || fb.getColorTextureView() == null) {
            return;
        }

        if (input == null) {
            input = new TextureTarget("Lumin Blur Input", fb.width, fb.height, false);
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

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        addBoxVertices(buffer, box);
        MeshData mesh = buffer.buildOrThrow();

        GpuBuffer vertices = this.boxPipeline.getVertexFormat().uploadImmediateVertexBuffer(mesh.vertexBuffer());
        RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
        GpuBuffer indices = autoIndices.getBuffer(mesh.drawState().indexCount());
        VertexFormat.IndexType indexType = autoIndices.type();
        GpuBufferSlice dynamicTransforms = LuminRenderSystem.writeTransform(
                RenderSystem.getModelViewMatrix(),
                new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                new Vector3f(),
                TextureTransform.DEFAULT_TEXTURING.getMatrix()
        );

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "Lumin 3D Box Blur",
                fb.getColorTextureView(), OptionalInt.empty(),
                fb.useDepth ? fb.getDepthTextureView() : null, OptionalDouble.empty()
        )) {
            renderPass.setPipeline(this.boxPipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("BoxBlurUniforms", boxBlurUniforms);
            renderPass.bindTexture("InputSampler", input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);
            renderPass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1);
        }

        mesh.close();
    }

    private void addBoxVertices(BufferBuilder buffer, AABB box) {
        Vec3 camPos = mc.getEntityRenderDispatcher().camera.position();

        float minX = (float) (box.minX - camPos.x);
        float minY = (float) (box.minY - camPos.y);
        float minZ = (float) (box.minZ - camPos.z);
        float maxX = (float) (box.maxX - camPos.x);
        float maxY = (float) (box.maxY - camPos.y);
        float maxZ = (float) (box.maxZ - camPos.z);

        Matrix4f matrix = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix;

        vertex(buffer, matrix, minX, minY, minZ);
        vertex(buffer, matrix, minX, minY, maxZ);
        vertex(buffer, matrix, maxX, minY, maxZ);
        vertex(buffer, matrix, maxX, minY, minZ);

        vertex(buffer, matrix, minX, maxY, minZ);
        vertex(buffer, matrix, maxX, maxY, minZ);
        vertex(buffer, matrix, maxX, maxY, maxZ);
        vertex(buffer, matrix, minX, maxY, maxZ);

        vertex(buffer, matrix, minX, minY, minZ);
        vertex(buffer, matrix, minX, maxY, minZ);
        vertex(buffer, matrix, maxX, maxY, minZ);
        vertex(buffer, matrix, maxX, minY, minZ);

        vertex(buffer, matrix, maxX, minY, minZ);
        vertex(buffer, matrix, maxX, maxY, minZ);
        vertex(buffer, matrix, maxX, maxY, maxZ);
        vertex(buffer, matrix, maxX, minY, maxZ);

        vertex(buffer, matrix, minX, minY, maxZ);
        vertex(buffer, matrix, maxX, minY, maxZ);
        vertex(buffer, matrix, maxX, maxY, maxZ);
        vertex(buffer, matrix, minX, maxY, maxZ);

        vertex(buffer, matrix, minX, minY, minZ);
        vertex(buffer, matrix, minX, minY, maxZ);
        vertex(buffer, matrix, minX, maxY, maxZ);
        vertex(buffer, matrix, minX, maxY, minZ);
    }

    private void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z) {
        buffer.addVertex(matrix, x, y, z).setColor(-1);
    }

    private static int blurUniformsSize() {
        Std140SizeCalculator calculator = new Std140SizeCalculator().putVec3().putVec4().putVec4().putVec4();
        for (int i = 0; i < MAX_SEGMENTS * 2; i++) {
            calculator.putVec4();
        }
        return calculator.get();
    }

    private static int clampSegmentCount(float[] segmentRects, int segmentCount) {
        if (segmentRects == null || segmentCount <= 0) {
            return 0;
        }
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
                    builder.putVec4(
                            segmentX * scale,
                            targetHeight - (segmentY + segmentHeight) * scale,
                            segmentWidth * scale,
                            segmentHeight * scale
                    );
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
