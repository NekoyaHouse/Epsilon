package com.github.epsilon.graphics.renderers;

import com.github.epsilon.graphics.LuminRenderPipelines;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.buffer.LuminRingBuffer;
import com.github.epsilon.holders.RendererHolder;
import com.github.epsilon.utils.render.ScissorUtils;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class RoundRectRenderer implements IRenderer {

    private static final long BUFFER_SIZE = 16 * 1024;
    private static final int STRIDE = 48;
    private static final long RECT_BYTES = STRIDE * 4L;
    private final LuminRingBuffer buffer = new LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX);

    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;
    private long currentOffset = 0;
    private int vertexCount = 0;
    private LuminRenderSystem.QuadRenderingInfo sharedInfo;

    private RoundRectRenderer() {
    }

    public static RoundRectRenderer create() {
        return RendererHolder.INSTANCE.register(new RoundRectRenderer());
    }

    public void addRoundRect(float x, float y, float width, float height, float radius, Color color) {
        addRoundRect(x, y, width, height, radius, radius, radius, radius, color);
    }

    public void addRoundRect(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, Color color) {
        addRoundRectGradient(x, y, width, height, rTL, rTR, rBR, rBL, color, color, color, color);
    }

    public void addVerticalGradient(float x, float y, float width, float height, float radius, Color top, Color bottom) {
        addRoundRectGradient(x, y, width, height, radius, radius, radius, radius, top, bottom, bottom, top);
    }

    public void addVerticalGradient(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, Color top, Color bottom) {
        addRoundRectGradient(x, y, width, height, rTL, rTR, rBR, rBL, top, bottom, bottom, top);
    }

    public void addHorizontalGradient(float x, float y, float width, float height, float radius, Color left, Color right) {
        addRoundRectGradient(x, y, width, height, radius, radius, radius, radius, left, left, right, right);
    }

    public void addHorizontalGradient(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, Color left, Color right) {
        addRoundRectGradient(x, y, width, height, rTL, rTR, rBR, rBL, left, left, right, right);
    }

    /**
     * 颜色顺序对应四个角顶点：左上、左下、右下、右上 (TL, BL, BR, TR)
     */
    public void addRoundRectGradient(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, Color cTL, Color cBL, Color cBR, Color cTR) {
        buffer.ensureCapacity(currentOffset + RECT_BYTES);
        buffer.tryMap();
        float x2 = x + width, y2 = y + height;
        int argbTL = ARGB.toABGR(cTL.getRGB());
        int argbBL = ARGB.toABGR(cBL.getRGB());
        int argbBR = ARGB.toABGR(cBR.getRGB());
        int argbTR = ARGB.toABGR(cTR.getRGB());

        addVertex(x, y, x, y, x2, y2, rTL, rTR, rBR, rBL, argbTL);
        addVertex(x, y2, x, y, x2, y2, rTL, rTR, rBR, rBL, argbBL);
        addVertex(x2, y2, x, y, x2, y2, rTL, rTR, rBR, rBL, argbBR);
        addVertex(x2, y, x, y, x2, y2, rTL, rTR, rBR, rBL, argbTR);
    }

    private void addVertex(float vx, float vy, float rx1, float ry1, float rx2, float ry2, float r1, float r2, float r3, float r4, int color) {
        long baseAddr = MemoryUtil.memAddress(buffer.getMappedBuffer());
        long p = baseAddr + currentOffset;
        MemoryUtil.memPutFloat(p, vx);
        MemoryUtil.memPutFloat(p + 4, vy);
        MemoryUtil.memPutFloat(p + 8, 0.0f);
        MemoryUtil.memPutInt(p + 12, color);
        MemoryUtil.memPutFloat(p + 16, rx1);
        MemoryUtil.memPutFloat(p + 20, ry1);
        MemoryUtil.memPutFloat(p + 24, rx2);
        MemoryUtil.memPutFloat(p + 28, ry2);
        MemoryUtil.memPutFloat(p + 32, r1);
        MemoryUtil.memPutFloat(p + 36, r2);
        MemoryUtil.memPutFloat(p + 40, r3);
        MemoryUtil.memPutFloat(p + 44, r4);
        currentOffset += STRIDE;
        vertexCount++;
    }

    @Override
    public void draw() {
        if (vertexCount == 0) return;
        if (buffer.isMapped()) buffer.unmap();

        LuminRenderSystem.QuadRenderingInfo info = LuminRenderSystem.prepareQuadRendering(vertexCount);
        if (info == null || info.colorView() == null) return;
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return;

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Round Rect Draw", info.colorView(), OptionalInt.empty(),
                info.depthView(), OptionalDouble.empty())
        ) {
            pass.setPipeline(LuminRenderPipelines.ROUND_RECT);
            if (scissorEnabled) ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", info.dynamicUniforms());
            drawPrepared(pass, info);
        }
    }

    @Override
    public boolean prepareSharedDraw() {
        sharedInfo = null;
        if (vertexCount == 0) return false;
        if (buffer.isMapped()) buffer.unmap();
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return false;

        sharedInfo = LuminRenderSystem.prepareQuadRendering(vertexCount, false);
        return sharedInfo != null && sharedInfo.colorView() != null;
    }

    @Override
    public void draw(RenderPass pass) {
        if (sharedInfo == null) return;
        pass.setUniform("DynamicTransforms", sharedInfo.dynamicUniforms());
        drawPrepared(pass, sharedInfo);
    }

    private void drawPrepared(RenderPass pass, LuminRenderSystem.QuadRenderingInfo info) {
        if (scissorEnabled) {
            if (!ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)) {
                return;
            }
        } else {
            pass.disableScissor();
        }

        pass.setVertexBuffer(0, buffer.getGpuBuffer());
        pass.setIndexBuffer(LuminRenderSystem.getQuadIndexBuffer(info.indexCount()), LuminRenderSystem.getQuadIndexType());
        pass.drawIndexed(0, 0, info.indexCount(), 1);
    }

    @Override
    public void clear() {
        if (vertexCount > 0) {
            if (buffer.isMapped()) buffer.unmap();
            buffer.rotate();
        }
        vertexCount = 0;
        currentOffset = 0;
        sharedInfo = null;
    }

    @Override
    public void close() {
        buffer.close();
        RendererHolder.INSTANCE.unregister(this);
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

}
