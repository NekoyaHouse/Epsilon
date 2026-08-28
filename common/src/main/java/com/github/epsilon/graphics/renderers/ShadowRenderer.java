package com.github.epsilon.graphics.renderers;

import com.github.epsilon.graphics.LuminRenderPipelines;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.buffer.LuminRingBuffer;
import com.github.epsilon.managers.RendererManager;
import com.github.epsilon.utils.render.ScissorUtils;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.List;

public class ShadowRenderer implements IRenderer {

    private static final long BUFFER_SIZE = 16 * 1024;
    private static final int STRIDE = 48;
    private static final long SHADOW_BYTES = STRIDE * 4L;
    private static final int SEGMENTED_STRIDE = 12;
    private static final long SEGMENTED_SHADOW_BYTES = SEGMENTED_STRIDE * 4L;
    private static final int MAX_SEGMENTS = 64;
    private static final int SEGMENTED_UNIFORMS_SIZE = segmentedUniformsSize();

    private final LuminRingBuffer buffer = new LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX);
    private final LuminRingBuffer segmentedBuffer = new LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX);
    private final List<SegmentedShadow> segmentedShadows = new ArrayList<>();
    private final List<PreparedSegmentedShadow> preparedSegmentedShadows = new ArrayList<>();
    private final List<ShadowDraw> draws = new ArrayList<>();

    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;
    private long currentOffset = 0;
    private long segmentedOffset = 0;
    private int vertexCount = 0;
    private int segmentedVertexCount = 0;
    private LuminRenderSystem.QuadRenderingInfo sharedInfo;

    private ShadowRenderer() {
    }

    public static ShadowRenderer create() {
        return RendererManager.INSTANCE.register(new ShadowRenderer());
    }

    public void addShadow(float x, float y, float width, float height, float radius, float blurRadius, Color color) {
        addShadow(x, y, width, height, radius, radius, radius, radius, blurRadius, color);
    }

    public void addShadow(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, float blurRadius, Color color) {
        int firstIndex = vertexCount / 4 * 6;
        float x2 = x + width;
        float y2 = y + height;
        float left = x - blurRadius;
        float top = y - blurRadius;
        float right = x2 + blurRadius;
        float bottom = y2 + blurRadius;

        buffer.ensureCapacity(currentOffset + SHADOW_BYTES);
        buffer.tryMap();

        int abgr = ARGB.toABGR(color.getRGB());

        addVertex(left, top, x, y, x2, y2, rTL, rTR, rBR, rBL, blurRadius, abgr);
        addVertex(left, bottom, x, y, x2, y2, rTL, rTR, rBR, rBL, blurRadius, abgr);
        addVertex(right, bottom, x, y, x2, y2, rTL, rTR, rBR, rBL, blurRadius, abgr);
        addVertex(right, top, x, y, x2, y2, rTL, rTR, rBR, rBL, blurRadius, abgr);
        appendNormalDraw(firstIndex);
    }

    public void addShadow(float x, float y, float width, float height, float radius, float blurRadius, Color color,
                          float[] segmentRects, float[] segmentRadii, int segmentCount) {
        addShadow(x, y, width, height, radius, radius, radius, radius, blurRadius, color,
                segmentRects, segmentRadii, null, segmentCount);
    }

    public void addShadow(float x, float y, float width, float height, float radius, float blurRadius, Color color,
                          float[] segmentRects, float[] segmentRadii, float[] segmentColors, int segmentCount) {
        addShadow(x, y, width, height, radius, radius, radius, radius, blurRadius, color,
                segmentRects, segmentRadii, segmentColors, segmentCount);
    }

    public void addShadow(float x, float y, float width, float height,
                          float rTL, float rTR, float rBR, float rBL, float blurRadius, Color color,
                          float[] segmentRects, float[] segmentRadii, float[] segmentColors, int segmentCount) {
        int count = clampSegmentCount(segmentRects, segmentCount);
        if (count == 0) {
            addShadow(x, y, width, height, rTL, rTR, rBR, rBL, blurRadius, color);
            return;
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            int offset = i * 4;
            float segmentX = segmentRects[offset];
            float segmentY = segmentRects[offset + 1];
            float segmentWidth = Math.max(0.0f, segmentRects[offset + 2]);
            float segmentHeight = Math.max(0.0f, segmentRects[offset + 3]);
            minX = Math.min(minX, segmentX);
            minY = Math.min(minY, segmentY);
            maxX = Math.max(maxX, segmentX + segmentWidth);
            maxY = Math.max(maxY, segmentY + segmentHeight);
        }

        if (!Float.isFinite(minX) || !Float.isFinite(minY) || maxX <= minX || maxY <= minY) {
            addShadow(x, y, width, height, rTL, rTR, rBR, rBL, blurRadius, color);
            return;
        }

        float range = Math.max(0.0f, blurRadius);
        float left = minX - range;
        float top = minY - range;
        float right = maxX + range;
        float bottom = maxY + range;

        segmentedBuffer.ensureCapacity(segmentedOffset + SEGMENTED_SHADOW_BYTES);
        segmentedBuffer.tryMap();
        addSegmentedVertex(left, top);
        addSegmentedVertex(left, bottom);
        addSegmentedVertex(right, bottom);
        addSegmentedVertex(right, top);

        float[] rects = Arrays.copyOf(segmentRects, count * 4);
        float[] radii = segmentRadii == null ? new float[count] : Arrays.copyOf(segmentRadii, count);
        int shadowIndex = segmentedShadows.size();
        float[] colors = segmentColors == null ? null : Arrays.copyOf(segmentColors, count * 3);
        segmentedShadows.add(new SegmentedShadow(range, color, rects, radii, colors, count));
        draws.add(new ShadowDraw(true, shadowIndex * 6, 6, shadowIndex));
    }

    private void addVertex(float x, float y, float innerX1, float innerY1, float innerX2, float innerY2,
                           float rTL, float rTR, float rBR, float rBL, float blurRadius, int color) {
        long address = MemoryUtil.memAddress(buffer.getMappedBuffer()) + currentOffset;

        MemoryUtil.memPutFloat(address, x);
        MemoryUtil.memPutFloat(address + 4, y);
        MemoryUtil.memPutFloat(address + 8, blurRadius);
        MemoryUtil.memPutInt(address + 12, color);

        MemoryUtil.memPutFloat(address + 16, innerX1);
        MemoryUtil.memPutFloat(address + 20, innerY1);
        MemoryUtil.memPutFloat(address + 24, innerX2);
        MemoryUtil.memPutFloat(address + 28, innerY2);

        MemoryUtil.memPutFloat(address + 32, rTL);
        MemoryUtil.memPutFloat(address + 36, rTR);
        MemoryUtil.memPutFloat(address + 40, rBR);
        MemoryUtil.memPutFloat(address + 44, rBL);

        currentOffset += STRIDE;
        vertexCount++;
    }

    private void addSegmentedVertex(float x, float y) {
        long address = MemoryUtil.memAddress(segmentedBuffer.getMappedBuffer()) + segmentedOffset;
        MemoryUtil.memPutFloat(address, x);
        MemoryUtil.memPutFloat(address + 4, y);
        MemoryUtil.memPutFloat(address + 8, 0.0f);
        segmentedOffset += SEGMENTED_STRIDE;
        segmentedVertexCount++;
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
        if (!prepare(true)) return;

        try {
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Lumin Shadow Draw", sharedInfo.colorView(), Optional.empty(),
                    sharedInfo.depthView(), OptionalDouble.empty())
            ) {
                RenderSystem.bindDefaultUniforms(pass);
                drawPrepared(pass, sharedInfo);
            }
        } finally {
            GlStateManager._disableScissorTest();
        }
    }

    @Override
    public boolean prepareSharedDraw() {
        return prepare(false);
    }

    private boolean prepare(boolean applyProjection) {
        sharedInfo = null;
        preparedSegmentedShadows.clear();
        if (vertexCount == 0 && segmentedVertexCount == 0) return false;
        if (buffer.isMapped()) buffer.unmap();
        if (segmentedBuffer.isMapped()) segmentedBuffer.unmap();
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return false;

        sharedInfo = LuminRenderSystem.prepareQuadRendering(Math.max(vertexCount, segmentedVertexCount), applyProjection);
        if (sharedInfo == null || sharedInfo.colorView() == null) return false;

        for (int i = 0; i < segmentedShadows.size(); i++) {
            SegmentedShadow shadow = segmentedShadows.get(i);
            GpuBufferSlice uniforms = LuminRenderSystem.writeDynamicUniform(
                    "segmented_shadow_uniforms",
                    "Lumin Segmented Shadow UBO",
                    SEGMENTED_UNIFORMS_SIZE,
                    16,
                    new SegmentedShadowUniforms(shadow)
            );
            preparedSegmentedShadows.add(new PreparedSegmentedShadow(uniforms));
        }
        return true;
    }

    @Override
    public void draw(RenderPass pass) {
        if (sharedInfo == null) return;
        try {
            pass.setUniform("DynamicTransforms", sharedInfo.dynamicUniforms());
            drawPrepared(pass, sharedInfo);
        } finally {
            GlStateManager._disableScissorTest();
        }
    }

    private void drawPrepared(RenderPass pass, LuminRenderSystem.QuadRenderingInfo info) {
        if (scissorEnabled) {
            if (!ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)) {
                return;
            }
        } else {
            pass.disableScissor();
        }

        pass.setIndexBuffer(LuminRenderSystem.getQuadIndexBuffer(info.indexCount()), LuminRenderSystem.getQuadIndexType());
        pass.setUniform("DynamicTransforms", info.dynamicUniforms());

        for (ShadowDraw draw : draws) {
            if (draw.segmented()) {
                PreparedSegmentedShadow shadow = preparedSegmentedShadows.get(draw.segmentedShadowIndex());
                pass.setPipeline(LuminRenderPipelines.SEGMENTED_SHADOW);
                pass.setVertexBuffer(0, segmentedBuffer.getGpuBuffer().slice());
                pass.setUniform("SegmentedShadowUniforms", shadow.uniforms());
                pass.drawIndexed(draw.indexCount(), 1, draw.firstIndex(), 0, 0);
            } else {
                pass.setPipeline(LuminRenderPipelines.SHADOW);
                pass.setVertexBuffer(0, buffer.getGpuBuffer().slice());
                pass.drawIndexed(draw.indexCount(), 1, draw.firstIndex(), 0, 0);
            }
        }
    }

    @Override
    public void clear() {
        if (vertexCount > 0) {
            if (buffer.isMapped()) buffer.unmap();
            buffer.rotate();
        }
        if (segmentedVertexCount > 0) {
            if (segmentedBuffer.isMapped()) segmentedBuffer.unmap();
            segmentedBuffer.rotate();
        }

        currentOffset = 0;
        segmentedOffset = 0;
        vertexCount = 0;
        segmentedVertexCount = 0;
        segmentedShadows.clear();
        preparedSegmentedShadows.clear();
        draws.clear();
        sharedInfo = null;
    }

    @Override
    public void close() {
        clear();
        buffer.close();
        segmentedBuffer.close();
        RendererManager.INSTANCE.unregister(this);
    }

    private static int clampSegmentCount(float[] segmentRects, int segmentCount) {
        if (segmentRects == null || segmentCount <= 0) {
            return 0;
        }
        return Math.min(MAX_SEGMENTS, Math.min(segmentCount, segmentRects.length / 4));
    }

    private void appendNormalDraw(int firstIndex) {
        if (!draws.isEmpty()) {
            ShadowDraw previous = draws.getLast();
            if (!previous.segmented() && previous.firstIndex() + previous.indexCount() == firstIndex) {
                draws.set(draws.size() - 1, new ShadowDraw(false, previous.firstIndex(), previous.indexCount() + 6, -1));
                return;
            }
        }
        draws.add(new ShadowDraw(false, firstIndex, 6, -1));
    }

    private static int segmentedUniformsSize() {
        Std140SizeCalculator calculator = new Std140SizeCalculator().putVec4().putVec4();
        for (int i = 0; i < MAX_SEGMENTS * 3; i++) {
            calculator.putVec4();
        }
        return calculator.get();
    }

    private record SegmentedShadow(
            float blurRadius, Color color, float[] rects, float[] radii, float[] colors, int count
    ) {
    }

    private record PreparedSegmentedShadow(GpuBufferSlice uniforms) {
    }

    private record ShadowDraw(boolean segmented, int firstIndex, int indexCount, int segmentedShadowIndex) {
    }

    private record SegmentedShadowUniforms(SegmentedShadow shadow) implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            Color color = shadow.color();
            Std140Builder builder = Std140Builder.intoBuffer(buffer)
                    .putVec4(
                            color.getRed() / 255.0f,
                            color.getGreen() / 255.0f,
                            color.getBlue() / 255.0f,
                            color.getAlpha() / 255.0f
                    )
                    .putVec4(shadow.blurRadius(), shadow.count(), 0.0f, 0.0f);

            for (int i = 0; i < MAX_SEGMENTS; i++) {
                if (i < shadow.count()) {
                    int offset = i * 4;
                    builder.putVec4(
                            shadow.rects()[offset],
                            shadow.rects()[offset + 1],
                            Math.max(0.0f, shadow.rects()[offset + 2]),
                            Math.max(0.0f, shadow.rects()[offset + 3])
                    );
                } else {
                    builder.putVec4(0.0f, 0.0f, 0.0f, 0.0f);
                }
            }

            for (int i = 0; i < MAX_SEGMENTS; i++) {
                float radius = i < shadow.count() ? Math.max(0.0f, shadow.radii()[i]) : 0.0f;
                builder.putVec4(radius, 0.0f, 0.0f, 0.0f);
            }

            for (int i = 0; i < MAX_SEGMENTS; i++) {
                if (shadow.colors() != null && i < shadow.count() && shadow.colors().length >= i * 3 + 3) {
                    int offset = i * 3;
                    builder.putVec4(shadow.colors()[offset], shadow.colors()[offset + 1], shadow.colors()[offset + 2], 1.0f);
                } else {
                    builder.putVec4(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, 1.0f);
                }
            }
        }
    }

}
