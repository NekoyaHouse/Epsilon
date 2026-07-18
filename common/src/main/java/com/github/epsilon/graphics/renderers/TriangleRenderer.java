package com.github.epsilon.graphics.renderers;

import com.github.epsilon.graphics.LuminRenderPipelines;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.buffer.LuminRingBuffer;
import com.github.epsilon.holders.RendererHolder;
import com.github.epsilon.utils.render.ScissorUtils;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.util.ARGB;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class TriangleRenderer implements IRenderer {

    private static final long BUFFER_SIZE = 16 * 1024;
    private static final int STRIDE = 16;
    private static final long TRIANGLE_BYTES = STRIDE * 3L;

    private final LuminRingBuffer buffer = new LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX);

    private long currentOffset = 0;
    private int vertexCount = 0;

    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;
    private GpuBufferSlice sharedDynamicUniforms;

    private TriangleRenderer() {
    }

    public static TriangleRenderer create() {
        return RendererHolder.INSTANCE.register(new TriangleRenderer());
    }

    public void addChevronTriangle(float centerX, float centerY, float size, float progress, Color color) {
        buffer.ensureCapacity(currentOffset + TRIANGLE_BYTES);
        buffer.tryMap();

        int abgr = ARGB.toABGR(color.getRGB());
        float clampedProgress = Math.clamp(progress, 0.0f, 1.0f);

        float tipXR = centerX + size;
        float tipYR = centerY;
        float base1XR = centerX - size;
        float base1YR = centerY - size;
        float base2XR = centerX - size;
        float base2YR = centerY + size;

        float tipXD = centerX;
        float tipYD = centerY + size;
        float base1XD = centerX - size;
        float base1YD = centerY - size;
        float base2XD = centerX + size;
        float base2YD = centerY - size;

        float x1 = base1XR + (base1XD - base1XR) * clampedProgress;
        float y1 = base1YR + (base1YD - base1YR) * clampedProgress;
        float x2 = base2XR + (base2XD - base2XR) * clampedProgress;
        float y2 = base2YR + (base2YD - base2YR) * clampedProgress;
        float x3 = tipXR + (tipXD - tipXR) * clampedProgress;
        float y3 = tipYR + (tipYD - tipYR) * clampedProgress;

        addVertex(x1, y1, abgr);
        addVertex(x2, y2, abgr);
        addVertex(x3, y3, abgr);
    }

    public void addTriangle(float x1, float y1, float x2, float y2, float x3, float y3, Color color) {
        buffer.ensureCapacity(currentOffset + TRIANGLE_BYTES);
        buffer.tryMap();

        int abgr = ARGB.toABGR(color.getRGB());
        addVertex(x1, y1, abgr);
        addVertex(x2, y2, abgr);
        addVertex(x3, y3, abgr);
    }

    private void addVertex(float vx, float vy, int color) {
        long baseAddr = MemoryUtil.memAddress(buffer.getMappedBuffer());
        long p = baseAddr + currentOffset;

        MemoryUtil.memPutFloat(p, vx);
        MemoryUtil.memPutFloat(p + 4, vy);
        MemoryUtil.memPutFloat(p + 8, 0.0f);
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

        LuminRenderSystem.applyOrthoProjection();

        GpuTextureView colorView = LuminRenderSystem.resolveColorView();
        GpuTextureView depthView = LuminRenderSystem.resolveDepthView();
        if (colorView == null) return;
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return;

        GpuBufferSlice dynamicUniforms = LuminRenderSystem.writeTransform(
                RenderSystem.getModelViewMatrix(),
                new Vector4f(1, 1, 1, 1),
                new Vector3f(0, 0, 0),
                TextureTransform.DEFAULT_TEXTURING.getMatrix()
        );

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Triangle Draw",
                colorView, OptionalInt.empty(),
                depthView, OptionalDouble.empty())
        ) {
            pass.setPipeline(LuminRenderPipelines.TRIANGLE);
            if (scissorEnabled) {
                ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH);
            }
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicUniforms);
            drawPrepared(pass);
        }
    }

    @Override
    public boolean prepareSharedDraw() {
        sharedDynamicUniforms = null;
        if (vertexCount == 0) return false;

        if (buffer.isMapped()) {
            buffer.unmap();
        }

        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return false;

        sharedDynamicUniforms = LuminRenderSystem.writeDefaultGuiTransform();
        return sharedDynamicUniforms != null;
    }

    @Override
    public void draw(RenderPass pass) {
        if (sharedDynamicUniforms == null) return;
        pass.setUniform("DynamicTransforms", sharedDynamicUniforms);
        drawPrepared(pass);
    }

    private void drawPrepared(RenderPass pass) {
        if (scissorEnabled) {
            if (!ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)) {
                return;
            }
        } else {
            pass.disableScissor();
        }

        pass.setVertexBuffer(0, buffer.getGpuBuffer());
        pass.draw(0, vertexCount);
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
        sharedDynamicUniforms = null;
    }

    @Override
    public void close() {
        clear();
        buffer.close();
        RendererHolder.INSTANCE.unregister(this);
    }

}
