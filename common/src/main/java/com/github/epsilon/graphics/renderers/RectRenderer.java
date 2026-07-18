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

public class RectRenderer implements IRenderer {

    private static final long BUFFER_SIZE = 16 * 1024;
    private static final int STRIDE = 16;
    private static final long RECT_BYTES = STRIDE * 4L;

    private final LuminRingBuffer buffer = new LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX);

    private long currentOffset = 0;
    private int vertexCount = 0;

    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;
    private LuminRenderSystem.QuadRenderingInfo sharedInfo;

    private RectRenderer() {
    }

    public static RectRenderer create() {
        return RendererHolder.INSTANCE.register(new RectRenderer());
    }

    public void addRect(float x, float y, float width, float height, Color color) {
        addRectGradient(x, y, width, height, color, color, color, color);
    }

    public void addOutline(float x, float y, float width, float height, float outline, Color color) {
        float sideHeight = height - outline * 2.0f;
        addRect(x, y, width, outline, color);
        addRect(x, y + height - outline, width, outline, color);
        addRect(x, y + outline, outline, sideHeight, color);
        addRect(x + width - outline, y + outline, outline, sideHeight, color);
    }

    public void addVerticalGradient(float x, float y, float width, float height, Color top, Color bottom) {
        addRectGradient(x, y, width, height, top, bottom, bottom, top);
    }

    public void addHorizontalGradient(float x, float y, float width, float height, Color left, Color right) {
        addRectGradient(x, y, width, height, left, left, right, right);
    }

    public void addRectGradient(float x, float y, float w, float h, Color c1, Color c2, Color c3, Color c4) {
        buffer.ensureCapacity(currentOffset + RECT_BYTES);
        buffer.tryMap();

        int argb1 = ARGB.toABGR(c1.getRGB());
        int argb2 = ARGB.toABGR(c2.getRGB());
        int argb3 = ARGB.toABGR(c3.getRGB());
        int argb4 = ARGB.toABGR(c4.getRGB());

        addVertex(x, y, argb1);
        addVertex(x, y + h, argb2);
        addVertex(x + w, y + h, argb3);
        addVertex(x + w, y, argb4);
    }

    private void addVertex(float vx, float vy, int color) {
        long baseAddr = MemoryUtil.memAddress(buffer.getMappedBuffer());
        long p = baseAddr + currentOffset;

        // Position: float x, y, z (12 bytes)
        MemoryUtil.memPutFloat(p, vx);
        MemoryUtil.memPutFloat(p + 4, vy);
        MemoryUtil.memPutFloat(p + 8, 0.0f);

        // Color: int abgr (4 bytes)
        MemoryUtil.memPutInt(p + 12, color);

        currentOffset += STRIDE;
        vertexCount++;
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
        if (vertexCount == 0) return;

        if (buffer.isMapped()) {
            buffer.unmap();
        }

        LuminRenderSystem.QuadRenderingInfo info = LuminRenderSystem.prepareQuadRendering(vertexCount);
        if (info == null || info.colorView() == null) return;
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return;

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Rect Draw",
                info.colorView(), OptionalInt.empty(),
                info.depthView(), OptionalDouble.empty())
        ) {
            pass.setPipeline(LuminRenderPipelines.RECTANGLE);
            if (scissorEnabled) {
                ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH);
            }

            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", info.dynamicUniforms());
            drawPrepared(pass, info);
        }
    }

    @Override
    public boolean prepareSharedDraw() {
        sharedInfo = null;
        if (vertexCount == 0) return false;

        if (buffer.isMapped()) {
            buffer.unmap();
        }

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
            if (buffer.isMapped()) {
                buffer.unmap();
            }
            buffer.rotate();
        }

        vertexCount = 0;
        currentOffset = 0;
        sharedInfo = null;
    }

    @Override
    public void close() {
        clear();
        buffer.close();
        RendererHolder.INSTANCE.unregister(this);
    }

}
