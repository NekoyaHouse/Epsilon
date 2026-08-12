package com.github.epsilon.graphics.renderers;

import com.github.epsilon.graphics.LuminRenderPipelines;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.buffer.LuminRingBuffer;
import com.github.epsilon.managers.RendererManager;
import com.github.epsilon.utils.render.ScissorUtils;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * 描边圆弧渲染器。
 * <p>
 * 每个圆弧提交一个覆盖其包围盒的四边形，由 SDF 片元着色器算出环体与角度扇区的交集，
 * 因此整条弧共享同一次距离场求值，不会像逐段三角形铺环那样在内部产生接缝。
 */
public class ArcRenderer implements IRenderer {

    private static final long BUFFER_SIZE = 16 * 1024;
    private static final int STRIDE = 56;
    private static final long ARC_BYTES = STRIDE * 4L;
    private static final float TAU = (float) (Math.PI * 2.0);

    private final LuminRingBuffer buffer = new LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX);

    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;
    private long currentOffset = 0;
    private int vertexCount = 0;
    private LuminRenderSystem.QuadRenderingInfo sharedInfo;

    private ArcRenderer() {
    }

    public static ArcRenderer create() {
        return RendererManager.INSTANCE.register(new ArcRenderer());
    }

    public void addCircle(float centerX, float centerY, float radius, float strokeWidth, Color color) {
        addArc(centerX, centerY, radius, strokeWidth, 0.0f, 360.0f, false, color);
    }

    /**
     * 提交一条描边圆弧。
     *
     * @param centerX      圆心 X
     * @param centerY      圆心 Y
     * @param radius       描边中线半径
     * @param strokeWidth  描边宽度
     * @param startDegrees 起始角度，0 指向右侧，顺时针增大
     * @param sweepDegrees 扫过角度
     * @param roundCap     是否使用圆形端帽
     * @param color        颜色
     */
    public void addArc(float centerX, float centerY, float radius, float strokeWidth,
                       float startDegrees, float sweepDegrees, boolean roundCap, Color color) {
        addGradientArc(centerX, centerY, radius, strokeWidth, startDegrees, sweepDegrees, roundCap,
                0.0f, color, color, color);
    }

    /**
     * 提交一条带三色循环扫描渐变的描边圆弧。
     *
     * @param gradientRotationDegrees 渐变首色所在角度，0 指向右侧，顺时针增大
     * @param startColor              渐变 0% 颜色
     * @param middleColor             渐变 50% 颜色
     * @param endColor                渐变 100% 颜色
     */
    public void addGradientArc(float centerX, float centerY, float radius, float strokeWidth,
                               float startDegrees, float sweepDegrees, boolean roundCap,
                               float gradientRotationDegrees, Color startColor, Color middleColor, Color endColor) {
        if (radius <= 0.0f || strokeWidth <= 0.0f) return;

        float sweep = Math.clamp(Math.abs(sweepDegrees), 0.0f, 360.0f);
        if (sweep <= 0.0f) return;

        float start = sweepDegrees < 0.0f ? startDegrees + sweepDegrees : startDegrees;

        buffer.ensureCapacity(currentOffset + ARC_BYTES);
        buffer.tryMap();

        float halfStroke = strokeWidth * 0.5f;
        // 包围盒外扩一像素，给 SDF 的抗锯齿过渡带留出空间。
        float extent = radius + halfStroke + 1.0f;
        float x1 = centerX - extent;
        float y1 = centerY - extent;
        float x2 = centerX + extent;
        float y2 = centerY + extent;

        float startRadians = (float) Math.toRadians(start);
        float sweepRadians = sweep >= 360.0f ? TAU : (float) Math.toRadians(sweep);
        float gradientRotationRadians = (float) Math.toRadians(gradientRotationDegrees);
        float cap = roundCap ? 1.0f : 0.0f;

        int startAbgr = ARGB.toABGR(startColor.getRGB());
        int middleAbgr = ARGB.toABGR(middleColor.getRGB());
        int endAbgr = ARGB.toABGR(endColor.getRGB());

        addVertex(x1, y1, centerX, centerY, radius, halfStroke, startRadians, sweepRadians, cap,
                gradientRotationRadians, startAbgr, middleAbgr, endAbgr);
        addVertex(x1, y2, centerX, centerY, radius, halfStroke, startRadians, sweepRadians, cap,
                gradientRotationRadians, startAbgr, middleAbgr, endAbgr);
        addVertex(x2, y2, centerX, centerY, radius, halfStroke, startRadians, sweepRadians, cap,
                gradientRotationRadians, startAbgr, middleAbgr, endAbgr);
        addVertex(x2, y1, centerX, centerY, radius, halfStroke, startRadians, sweepRadians, cap,
                gradientRotationRadians, startAbgr, middleAbgr, endAbgr);
    }

    private void addVertex(float vx, float vy, float centerX, float centerY, float radius, float halfStroke,
                           float startRadians, float sweepRadians, float roundCap, float gradientRotationRadians,
                           int startColor, int middleColor, int endColor) {
        long baseAddr = MemoryUtil.memAddress(buffer.getMappedBuffer());
        long p = baseAddr + currentOffset;
        MemoryUtil.memPutFloat(p, vx);
        MemoryUtil.memPutFloat(p + 4, vy);
        MemoryUtil.memPutFloat(p + 8, 0.0f);
        MemoryUtil.memPutInt(p + 12, startColor);
        MemoryUtil.memPutInt(p + 16, middleColor);
        MemoryUtil.memPutInt(p + 20, endColor);
        MemoryUtil.memPutFloat(p + 24, centerX);
        MemoryUtil.memPutFloat(p + 28, centerY);
        MemoryUtil.memPutFloat(p + 32, radius);
        MemoryUtil.memPutFloat(p + 36, halfStroke);
        MemoryUtil.memPutFloat(p + 40, startRadians);
        MemoryUtil.memPutFloat(p + 44, sweepRadians);
        MemoryUtil.memPutFloat(p + 48, roundCap);
        MemoryUtil.memPutFloat(p + 52, gradientRotationRadians);
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
                () -> "Arc Draw", info.colorView(), Optional.empty(),
                info.depthView(), OptionalDouble.empty())
        ) {
            pass.setPipeline(LuminRenderPipelines.ARC);
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

        pass.setVertexBuffer(0, buffer.getGpuBuffer().slice());
        pass.setIndexBuffer(LuminRenderSystem.getQuadIndexBuffer(info.indexCount()), LuminRenderSystem.getQuadIndexType());
        pass.drawIndexed(info.indexCount(), 1, 0, 0, 0);
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
        RendererManager.INSTANCE.unregister(this);
    }

}
