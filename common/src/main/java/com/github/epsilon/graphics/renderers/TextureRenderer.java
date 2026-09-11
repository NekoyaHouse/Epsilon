package com.github.epsilon.graphics.renderers;

import com.github.epsilon.graphics.LuminRenderPipelines;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.LuminTexture;
import com.github.epsilon.graphics.buffer.LuminRingBuffer;
import com.github.epsilon.managers.RendererManager;
import com.github.epsilon.utils.render.ScissorUtils;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static com.github.epsilon.Constants.mc;

public class TextureRenderer implements IRenderer {

    private static final int STRIDE = 56;
    private static final long BUFFER_SIZE = 16 * 1024;
    private static final long QUAD_BYTES = STRIDE * 4L;

    private final Map<Object, Batch> batches = new LinkedHashMap<>();
    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;
    private GpuBufferSlice sharedDynamicUniforms;
    private int sharedMaxIndexCount;

    private TextureRenderer() {
    }

    public static TextureRenderer create() {
        return RendererManager.INSTANCE.register(new TextureRenderer());
    }

    public void addQuadTexture(LuminTexture texture, float x, float y, float width, float height, float u0, float v0, float u1, float v1, Color color) {
        addRoundedTexture(texture, x, y, width, height, 0f, u0, v0, u1, v1, color);
    }

    public void addQuadTexture(Identifier texture, float x, float y, float width, float height, float u0, float v0, float u1, float v1, Color color) {
        addRoundedTexture(texture, x, y, width, height, 0f, u0, v0, u1, v1, color, false);
    }

    public void addQuadTexture(Identifier texture, float x, float y, float width, float height, float u0, float v0, float u1, float v1, Color color, boolean useLinearFilter) {
        addRoundedTexture(texture, x, y, width, height, 0f, u0, v0, u1, v1, color, useLinearFilter);
    }

    public void addRoundedTexture(Identifier texture, float x, float y, float width, float height, float radius, float u0, float v0, float u1, float v1, Color color) {
        addRoundedTexture(texture, x, y, width, height, radius, u0, v0, u1, v1, color, false);
    }

    public void addRoundedTexture(Identifier texture, float x, float y, float width, float height, float radius, float u0, float v0, float u1, float v1, Color color, boolean useLinearFilter) {
        addRoundedTexture((Object) texture, x, y, width, height, radius, radius, radius, radius, u0, v0, u1, v1, color, useLinearFilter);
    }

    public void addRoundedTexture(LuminTexture texture, float x, float y, float width, float height, float radius, float u0, float v0, float u1, float v1, Color color) {
        addRoundedTexture(texture, x, y, width, height, radius, radius, radius, radius, u0, v0, u1, v1, color, true);
    }

    public void addRoundedTexture(Identifier texture, float x, float y, float width, float height, float radiusTL, float radiusTR, float radiusBR, float radiusBL, float u0, float v0, float u1, float v1, Color color, boolean useLinearFilter) {
        addRoundedTexture((Object) texture, x, y, width, height, radiusTL, radiusTR, radiusBR, radiusBL, u0, v0, u1, v1, color, useLinearFilter);
    }

    public void addRoundedTexture(LuminTexture texture, float x, float y, float width, float height, float radiusTL, float radiusTR, float radiusBR, float radiusBL, float u0, float v0, float u1, float v1, Color color) {
        addRoundedTexture(texture, x, y, width, height, radiusTL, radiusTR, radiusBR, radiusBL, u0, v0, u1, v1, color, true);
    }

    public void addRotatedTexture(Identifier texture, float x, float y, float width, float height, float u0, float v0, float u1, float v1, Color color, float originX, float originY, float rotationDegrees, boolean useLinearFilter) {
        addRotatedRoundedTexture(texture, x, y, width, height, 0.0f, 0.0f, 0.0f, 0.0f,
                u0, v0, u1, v1, color, originX, originY, rotationDegrees, useLinearFilter);
    }

    public void addRotatedTexture(LuminTexture texture, float x, float y, float width, float height, float u0, float v0, float u1, float v1, Color color, float originX, float originY, float rotationDegrees) {
        addRotatedRoundedTexture(texture, x, y, width, height, 0.0f, 0.0f, 0.0f, 0.0f, u0, v0, u1, v1, color, originX, originY, rotationDegrees);
    }

    public void addRotatedRoundedTexture(
            Identifier texture, float x, float y, float width, float height,
            float radiusTopLeft, float radiusTopRight,
            float radiusBottomRight, float radiusBottomLeft,
            float u0, float v0, float u1, float v1, Color color,
            float originX, float originY, float rotationDegrees, boolean useLinearFilter
    ) {
        addRotatedRoundedTexture((Object) texture, x, y, width, height,
                radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                u0, v0, u1, v1, color, originX, originY, rotationDegrees, useLinearFilter);
    }

    public void addRotatedRoundedTexture(
            LuminTexture texture, float x, float y, float width, float height,
            float radiusTopLeft, float radiusTopRight,
            float radiusBottomRight, float radiusBottomLeft,
            float u0, float v0, float u1, float v1, Color color,
            float originX, float originY, float rotationDegrees
    ) {
        addRotatedRoundedTexture(texture, x, y, width, height,
                radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                u0, v0, u1, v1, color, originX, originY, rotationDegrees, true);
    }

    private void addRoundedTexture(Object textureKey, float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, float u0, float v0, float u1, float v1, Color color, boolean useLinearFilter) {
        Batch batch = batches.computeIfAbsent(new TextureKey(textureKey, useLinearFilter),
                key -> new Batch(new LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX)));

        batch.buffer.ensureCapacity(batch.currentOffset + QUAD_BYTES);
        batch.buffer.tryMap();

        int argb = ARGB.toABGR(color.getRGB());

        float x2 = x + width;
        float y2 = y + height;

        long baseAddr = MemoryUtil.memAddress(batch.buffer.getMappedBuffer());
        long p = baseAddr + batch.currentOffset;

        writeVertex(p, x, y, u0, v0, argb, 0.0f, 0.0f, width, height, rTL, rTR, rBR, rBL);
        writeVertex(p + STRIDE, x, y2, u0, v1, argb, 0.0f, height, width, height, rTL, rTR, rBR, rBL);
        writeVertex(p + STRIDE * 2L, x2, y2, u1, v1, argb, width, height, width, height, rTL, rTR, rBR, rBL);
        writeVertex(p + STRIDE * 3L, x2, y, u1, v0, argb, width, 0.0f, width, height, rTL, rTR, rBR, rBL);

        batch.currentOffset += QUAD_BYTES;
        batch.vertexCount += 4;
    }

    private void addRotatedRoundedTexture(
            Object textureKey, float x, float y, float width, float height,
            float rTL, float rTR, float rBR, float rBL,
            float u0, float v0, float u1, float v1, Color color,
            float originX, float originY, float rotationDegrees, boolean useLinearFilter
    ) {
        Batch batch = batches.computeIfAbsent(new TextureKey(textureKey, useLinearFilter),
                key -> new Batch(new LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX)));

        batch.buffer.ensureCapacity(batch.currentOffset + QUAD_BYTES);
        batch.buffer.tryMap();

        int argb = ARGB.toABGR(color.getRGB());
        float x2 = x + width;
        float y2 = y + height;
        float radians = (float) Math.toRadians(rotationDegrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);

        float rx1 = rotateX(x, y, originX, originY, cos, sin);
        float ry1 = rotateY(x, y, originX, originY, cos, sin);
        float rx2 = rotateX(x, y2, originX, originY, cos, sin);
        float ry2 = rotateY(x, y2, originX, originY, cos, sin);
        float rx3 = rotateX(x2, y2, originX, originY, cos, sin);
        float ry3 = rotateY(x2, y2, originX, originY, cos, sin);
        float rx4 = rotateX(x2, y, originX, originY, cos, sin);
        float ry4 = rotateY(x2, y, originX, originY, cos, sin);
        long baseAddr = MemoryUtil.memAddress(batch.buffer.getMappedBuffer());
        long p = baseAddr + batch.currentOffset;

        writeVertex(p, rx1, ry1, u0, v0, argb, 0.0f, 0.0f, width, height, rTL, rTR, rBR, rBL);
        writeVertex(p + STRIDE, rx2, ry2, u0, v1, argb, 0.0f, height, width, height, rTL, rTR, rBR, rBL);
        writeVertex(p + STRIDE * 2L, rx3, ry3, u1, v1, argb, width, height, width, height, rTL, rTR, rBR, rBL);
        writeVertex(p + STRIDE * 3L, rx4, ry4, u1, v0, argb, width, 0.0f, width, height, rTL, rTR, rBR, rBL);

        batch.currentOffset += QUAD_BYTES;
        batch.vertexCount += 4;
    }

    private static float rotateX(float x, float y, float originX, float originY, float cos, float sin) {
        float dx = x - originX;
        float dy = y - originY;
        return originX + dx * cos - dy * sin;
    }

    private static float rotateY(float x, float y, float originX, float originY, float cos, float sin) {
        float dx = x - originX;
        float dy = y - originY;
        return originY + dx * sin + dy * cos;
    }

    private void writeVertex(long addr, float x, float y, float u, float v, int color, float localX, float localY, float localWidth, float localHeight, float r1, float r2, float r3, float r4) {
        MemoryUtil.memPutFloat(addr, x);
        MemoryUtil.memPutFloat(addr + 4, y);
        MemoryUtil.memPutFloat(addr + 8, 0.0f); // z
        MemoryUtil.memPutInt(addr + 12, color);
        MemoryUtil.memPutFloat(addr + 16, u);
        MemoryUtil.memPutFloat(addr + 20, v);
        MemoryUtil.memPutFloat(addr + 24, localX);
        MemoryUtil.memPutFloat(addr + 28, localY);
        MemoryUtil.memPutFloat(addr + 32, localWidth);
        MemoryUtil.memPutFloat(addr + 36, localHeight);
        // Radius vector (TL, TR, BR, BL)
        MemoryUtil.memPutFloat(addr + 40, r1);
        MemoryUtil.memPutFloat(addr + 44, r2);
        MemoryUtil.memPutFloat(addr + 48, r3);
        MemoryUtil.memPutFloat(addr + 52, r4);
    }

    public void setScissor(int x, int y, int width, int height) {
        LuminRenderSystem.ScissorRect scissor = ScissorUtils.clampFramebufferScissor(x, y, width, height);
        scissorEnabled = true;
        scissorX = scissor.x();
        scissorY = scissor.y();
        scissorW = scissor.width();
        scissorH = scissor.height();
    }

    public void clearScissor() {
        scissorEnabled = false;
    }

    @Override
    public void draw() {
        if (batches.isEmpty()) return;

        LuminRenderSystem.applyOrthoProjection();

        GpuTextureView colorView = LuminRenderSystem.resolveColorView();
        if (colorView == null) return;
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return;

        int maxIndexCount = prepareTextureBatches();
        if (maxIndexCount == 0) return;

        GpuBufferSlice dynamicUniforms = LuminRenderSystem.writeDefaultGuiTransform();
        GpuBuffer ibo = LuminRenderSystem.getQuadIndexBuffer(maxIndexCount);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Rounded Texture Draws",
                colorView, Optional.empty(),
                null, OptionalDouble.empty())
        ) {
            pass.setPipeline(LuminRenderPipelines.TEXTURE);
            if (scissorEnabled) {
                ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH);
            }

            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicUniforms);
            pass.setIndexBuffer(ibo, LuminRenderSystem.getQuadIndexType());

            drawPrepared(pass);
        }
    }

    @Override
    public boolean prepareSharedDraw() {
        sharedDynamicUniforms = null;
        sharedMaxIndexCount = 0;
        if (batches.isEmpty()) return false;
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return false;

        sharedMaxIndexCount = prepareTextureBatches();
        if (sharedMaxIndexCount == 0) return false;

        LuminRenderSystem.getQuadIndexBuffer(sharedMaxIndexCount);
        sharedDynamicUniforms = LuminRenderSystem.writeDefaultGuiTransform();
        return sharedDynamicUniforms != null;
    }

    @Override
    public void draw(RenderPass pass) {
        if (sharedDynamicUniforms == null || sharedMaxIndexCount == 0) return;

        pass.setIndexBuffer(LuminRenderSystem.getQuadIndexBuffer(sharedMaxIndexCount), LuminRenderSystem.getQuadIndexType());
        pass.setUniform("DynamicTransforms", sharedDynamicUniforms);
        drawPrepared(pass);
    }

    private int prepareTextureBatches() {
        int maxIndexCount = 0;
        for (Map.Entry<Object, Batch> entry : batches.entrySet()) {
            Batch batch = entry.getValue();
            batch.preparedTexture = null;
            if (batch.vertexCount == 0) continue;

            if (batch.buffer.isMapped()) {
                batch.buffer.unmap();
            }

            batch.preparedTexture = resolveTexture((TextureKey) entry.getKey());
            if (batch.preparedTexture == null) continue;
            maxIndexCount = Math.max(maxIndexCount, (batch.vertexCount / 4) * 6);
        }
        return maxIndexCount;
    }

    private PreparedTexture resolveTexture(TextureKey key) {
        if (key.texture() instanceof Identifier identifier) {
            AbstractTexture texture = mc.getTextureManager().getTexture(identifier);
            GpuSampler sampler = key.linearFilter()
                    ? RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                    : texture.getSampler();
            return new PreparedTexture(texture.getTextureView(), sampler);
        }
        if (key.texture() instanceof LuminTexture texture) {
            return new PreparedTexture(texture.getTextureView(), texture.getSampler());
        }
        return null;
    }

    private void drawPrepared(RenderPass pass) {
        if (scissorEnabled) {
            if (!ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)) {
                return;
            }
        } else {
            pass.disableScissor();
        }

        // 纹理解析和上传已经在 prepare 阶段完成，pass 内只允许绑定和 draw。
        for (Batch batch : batches.values()) {
            if (batch.vertexCount == 0 || batch.preparedTexture == null) continue;

            int indexCount = (batch.vertexCount / 4) * 6;
            PreparedTexture texture = batch.preparedTexture;

            pass.setVertexBuffer(0, batch.buffer.getGpuBuffer().slice());
            pass.bindTexture("Sampler0", texture.view(), texture.sampler());
            pass.drawIndexed(indexCount, 1, 0, 0, 0);
        }
    }

    @Override
    public void clear() {
        for (Batch batch : batches.values()) {
            if (batch.vertexCount > 0) {
                if (batch.buffer.isMapped()) {
                    batch.buffer.unmap();
                }
                batch.buffer.rotate();
            }
            batch.currentOffset = 0;
            batch.vertexCount = 0;
            batch.preparedTexture = null;
        }
        sharedDynamicUniforms = null;
        sharedMaxIndexCount = 0;
    }

    @Override
    public void close() {
        clear();
        for (Batch batch : batches.values()) {
            batch.buffer.close();
        }
        batches.clear();
        RendererManager.INSTANCE.unregister(this);
    }

    private record TextureKey(Object texture, boolean linearFilter) {
    }

    private record PreparedTexture(GpuTextureView view, GpuSampler sampler) {
    }

    private static final class Batch {
        final LuminRingBuffer buffer;
        long currentOffset = 0;
        int vertexCount = 0;
        PreparedTexture preparedTexture;

        private Batch(LuminRingBuffer buffer) {
            this.buffer = buffer;
        }
    }

}
