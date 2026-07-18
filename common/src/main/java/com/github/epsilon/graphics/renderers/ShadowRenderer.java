package com.github.epsilon.graphics.renderers;

import com.github.epsilon.graphics.LuminRenderPipelines;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.buffer.LuminRingBuffer;
import com.github.epsilon.holders.RendererHolder;
import com.github.epsilon.utils.render.ScissorUtils;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class ShadowRenderer implements IRenderer {

    private static final long BUFFER_SIZE = 16 * 1024;
    private static final int STRIDE = 48;
    private static final long SHADOW_BYTES = STRIDE * 4L;

    private final LuminRingBuffer buffer = new LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX);

    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;
    private long currentOffset = 0;
    private int vertexCount = 0;
    private LuminRenderSystem.QuadRenderingInfo sharedInfo;

    private ShadowRenderer() {
    }

    public static ShadowRenderer create() {
        return RendererHolder.INSTANCE.register(new ShadowRenderer());
    }

    public void addShadow(float x, float y, float width, float height, float radius, float blurRadius, Color color) {
        addShadow(x, y, width, height, radius, radius, radius, radius, blurRadius, color);
    }

    public void addShadow(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, float blurRadius, Color color) {
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
        if (buffer.isMapped()) buffer.unmap();

        LuminRenderSystem.QuadRenderingInfo info = LuminRenderSystem.prepareQuadRendering(vertexCount);
        if (info == null || info.colorView() == null) return;
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return;

        try {
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Lumin Shadow Draw", info.colorView(), OptionalInt.empty(),
                    info.depthView(), OptionalDouble.empty())
            ) {
                pass.setPipeline(LuminRenderPipelines.SHADOW);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", info.dynamicUniforms());
                drawPrepared(pass, info);
            }
        } finally {
            GlStateManager._disableScissorTest();
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

        currentOffset = 0;
        vertexCount = 0;
        sharedInfo = null;
    }

    @Override
    public void close() {
        clear();
        buffer.close();
        RendererHolder.INSTANCE.unregister(this);
    }

}
