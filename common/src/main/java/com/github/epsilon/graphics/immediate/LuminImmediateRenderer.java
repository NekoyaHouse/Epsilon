package com.github.epsilon.graphics.immediate;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.buffer.LuminRingBuffer;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nullable;
import java.nio.ByteOrder;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static com.github.epsilon.Constants.mc;

public final class LuminImmediateRenderer {

    private static final long DEFAULT_BUFFER_SIZE = 1024 * 1024;
    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private static final Channel POS_COLOR_QUADS = new Channel(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS);
    private static final Channel POS_COLOR_TRIANGLE_STRIP = new Channel(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP);
    private static final Channel POS_COLOR_TRIANGLE_FAN = new Channel(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_FAN);
    private static final Channel POS_TEX_COLOR_QUADS = new Channel(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS);
    private static final Channel POS_COLOR_NORMAL_LINE_WIDTH_LINES = new Channel(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.Mode.LINES);

    private LuminImmediateRenderer() {
    }

    public static PosColorQuads beginPosColorQuads(RenderPipeline pipeline) {
        return new PosColorQuads(POS_COLOR_QUADS.begin(pipeline, null));
    }

    public static PosColorTriangleStrip beginPosColorTriangleStrip(RenderPipeline pipeline) {
        return new PosColorTriangleStrip(POS_COLOR_TRIANGLE_STRIP.begin(pipeline, null));
    }

    public static PosColorTriangleFan beginPosColorTriangleFan(RenderPipeline pipeline) {
        return new PosColorTriangleFan(POS_COLOR_TRIANGLE_FAN.begin(pipeline, null));
    }

    public static PosTexColorQuads beginPosTexColorQuads(RenderPipeline pipeline, Identifier texture) {
        return new PosTexColorQuads(POS_TEX_COLOR_QUADS.begin(pipeline, texture));
    }

    public static Lines beginLines(RenderPipeline pipeline) {
        return new Lines(POS_COLOR_NORMAL_LINE_WIDTH_LINES.begin(pipeline, null));
    }

    public static void endFrame() {
        POS_COLOR_QUADS.endFrame();
        POS_COLOR_TRIANGLE_STRIP.endFrame();
        POS_COLOR_TRIANGLE_FAN.endFrame();
        POS_TEX_COLOR_QUADS.endFrame();
        POS_COLOR_NORMAL_LINE_WIDTH_LINES.endFrame();
    }

    public static final class PosColorQuads {

        private final Channel channel;

        private PosColorQuads(Channel channel) {
            this.channel = channel;
        }

        public void vertex(Matrix4f matrix, float x, float y, float z, int color) {
            this.channel.putPosition(matrix, x, y, z);
            this.channel.putColor(color);
            this.channel.finishVertex();
        }

        public void end() {
            this.channel.drawAndReset();
        }
    }

    public static final class PosColorTriangleStrip {

        private final Channel channel;

        private PosColorTriangleStrip(Channel channel) {
            this.channel = channel;
        }

        public void vertex(Matrix4f matrix, float x, float y, float z, int color) {
            this.channel.putPosition(matrix, x, y, z);
            this.channel.putColor(color);
            this.channel.finishVertex();
        }

        public void end() {
            this.channel.drawAndReset();
        }

    }

    public static final class PosColorTriangleFan {

        private final Channel channel;

        private PosColorTriangleFan(Channel channel) {
            this.channel = channel;
        }

        public void vertex(Matrix4f matrix, float x, float y, float z, int color) {
            this.channel.putPosition(matrix, x, y, z);
            this.channel.putColor(color);
            this.channel.finishVertex();
        }

        public void end() {
            this.channel.drawAndReset();
        }

    }

    public static final class PosTexColorQuads {

        private final Channel channel;

        private PosTexColorQuads(Channel channel) {
            this.channel = channel;
        }

        public void vertex(Matrix4f matrix, float x, float y, float z, float u, float v, int color) {
            this.channel.putPosition(matrix, x, y, z);
            this.channel.putUv(u, v);
            this.channel.putColor(color);
            this.channel.finishVertex();
        }

        public void end() {
            this.channel.drawAndReset();
        }
    }

    public static final class Lines {

        private final Channel channel;
        private final Vector3f normalTmp = new Vector3f();

        private Lines(Channel channel) {
            this.channel = channel;
        }

        public void vertex(Matrix4f matrix, PoseStack.Pose pose, float x, float y, float z, int color, float nx, float ny, float nz, float width) {
            this.channel.putPosition(matrix, x, y, z);
            this.channel.putColor(color);

            pose.transformNormal(nx, ny, nz, this.normalTmp).normalize();
            this.channel.putNormal(this.normalTmp.x, this.normalTmp.y, this.normalTmp.z);
            this.channel.putLineWidth(width);
            this.channel.finishVertex();
        }

        public void end() {
            this.channel.drawAndReset();
        }
    }

    private static final class Channel {

        private final LuminRingBuffer ringBuffer;
        private final VertexFormat format;
        private final VertexFormat.Mode mode;
        private final int stride;

        private final int positionOffset;
        private final int colorOffset;
        private final int uvOffset;
        private final int normalOffset;
        private final int lineWidthOffset;

        private final Vector3f posTmp = new Vector3f();

        private boolean building;
        private long currentOffset;
        private long frameOffset;
        private long batchStartOffset;
        private int vertexCount;
        private boolean frameUsed;

        private long vertexBaseAddr;

        private RenderPipeline pipeline;
        @Nullable
        private Identifier texture;

        private Channel(VertexFormat format, VertexFormat.Mode mode) {
            this.ringBuffer = new LuminRingBuffer(DEFAULT_BUFFER_SIZE, GpuBuffer.USAGE_VERTEX);
            this.format = format;
            this.mode = mode;
            this.stride = format.getVertexSize();

            this.positionOffset = resolveOffset(format, VertexFormatElement.POSITION);
            this.colorOffset = resolveOffset(format, VertexFormatElement.COLOR);
            this.uvOffset = resolveOffset(format, VertexFormatElement.UV0);
            this.normalOffset = resolveOffset(format, VertexFormatElement.NORMAL);
            this.lineWidthOffset = resolveOffset(format, VertexFormatElement.LINE_WIDTH);
        }

        private static int resolveOffset(VertexFormat format, VertexFormatElement element) {
            return format.contains(element) ? format.getOffset(element) : -1;
        }

        private Channel begin(RenderPipeline pipeline, @Nullable Identifier texture) {
            if (this.building) {
                throw new IllegalStateException("Immediate channel is already building");
            }
            this.building = true;
            this.currentOffset = this.frameOffset;
            this.batchStartOffset = this.frameOffset;
            this.vertexCount = 0;
            this.pipeline = pipeline;
            this.texture = texture;

            this.ringBuffer.tryMap();
            return this;
        }

        private void putPosition(Matrix4f matrix, float x, float y, float z) {
            if (this.positionOffset < 0 || !ensureCapacity()) {
                return;
            }
            matrix.transformPosition(x, y, z, this.posTmp);
            long p = this.vertexBaseAddr + this.positionOffset;
            MemoryUtil.memPutFloat(p, this.posTmp.x);
            MemoryUtil.memPutFloat(p + 4L, this.posTmp.y);
            MemoryUtil.memPutFloat(p + 8L, this.posTmp.z);
        }

        private void putColor(int color) {
            if (this.colorOffset < 0 || !ensureCapacity()) {
                return;
            }
            int abgr = ARGB.toABGR(color);
            long p = this.vertexBaseAddr + this.colorOffset;
            MemoryUtil.memPutInt(p, LITTLE_ENDIAN ? abgr : Integer.reverseBytes(abgr));
        }

        private void putUv(float u, float v) {
            if (this.uvOffset < 0 || !ensureCapacity()) {
                return;
            }
            long p = this.vertexBaseAddr + this.uvOffset;
            MemoryUtil.memPutFloat(p, u);
            MemoryUtil.memPutFloat(p + 4L, v);
        }

        private void putNormal(float nx, float ny, float nz) {
            if (this.normalOffset < 0 || !ensureCapacity()) {
                return;
            }
            long p = this.vertexBaseAddr + this.normalOffset;
            MemoryUtil.memPutByte(p, packNormal(nx));
            MemoryUtil.memPutByte(p + 1L, packNormal(ny));
            MemoryUtil.memPutByte(p + 2L, packNormal(nz));
        }

        private void putLineWidth(float width) {
            if (this.lineWidthOffset < 0 || !ensureCapacity()) {
                return;
            }
            MemoryUtil.memPutFloat(this.vertexBaseAddr + this.lineWidthOffset, width);
        }

        private void finishVertex() {
            if (!this.building || this.vertexBaseAddr == 0L) {
                return;
            }
            long completedVertexBaseAddr = this.vertexBaseAddr;
            this.currentOffset += this.stride;
            this.vertexCount++;

            if (this.mode == VertexFormat.Mode.LINES) {
                long duplicateVertexBaseAddr = MemoryUtil.memAddress(this.ringBuffer.getMappedBuffer()) + this.currentOffset;
                MemoryUtil.memCopy(completedVertexBaseAddr, duplicateVertexBaseAddr, this.stride);
                this.currentOffset += this.stride;
                this.vertexCount++;
            }

            this.vertexBaseAddr = 0L;
        }

        private boolean ensureCapacity() {
            if (!this.building) {
                return false;
            }
            if (this.vertexBaseAddr != 0L) {
                return true;
            }
            long requiredBytes = this.mode == VertexFormat.Mode.LINES ? this.stride * 2L : this.stride;
            this.ringBuffer.ensureCapacity(this.currentOffset + requiredBytes);
            if (!this.ringBuffer.isMapped()) {
                this.ringBuffer.tryMap();
            }
            this.vertexBaseAddr = MemoryUtil.memAddress(this.ringBuffer.getMappedBuffer()) + this.currentOffset;
            return true;
        }

        private void drawAndReset() {
            boolean submittedDraw = false;
            long completedOffset = this.currentOffset;

            try {
                if (this.vertexCount <= 0) {
                    return;
                }

                if (this.ringBuffer.isMapped()) {
                    this.ringBuffer.unmap();
                }

                GpuTextureView colorView = LuminRenderSystem.resolveColorView();
                GpuTextureView depthView = LuminRenderSystem.resolveDepthView();

                if (colorView == null) {
                    return;
                }

                GpuBufferSlice dynamicUniforms = RenderSystem.getDynamicUniforms().writeTransform(
                        RenderSystem.getModelViewMatrix(),
                        new Vector4f(1, 1, 1, 1),
                        new Vector3f(0, 0, 0),
                        TextureTransform.DEFAULT_TEXTURING.getMatrix()
                );

                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Lumin Immediate Draw",
                        colorView, OptionalInt.empty(),
                        depthView, OptionalDouble.empty())
                ) {
                    pass.setPipeline(this.pipeline);
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("DynamicTransforms", dynamicUniforms);
                    pass.setVertexBuffer(0, this.ringBuffer.getGpuBuffer());

                    if (this.texture != null) {
                        AbstractTexture textureObject = mc.getTextureManager().getTexture(this.texture);
                        pass.bindTexture("Sampler0", textureObject.getTextureView(), textureObject.getSampler());
                    }

                    switch (this.mode) {
                        case LINES, QUADS -> {
                            int indexCount = this.mode.indexCount(this.vertexCount);
                            if (indexCount > 0) {
                                RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(this.mode);
                                GpuBuffer ibo = autoIndices.getBuffer(indexCount);
                                pass.setIndexBuffer(ibo, autoIndices.type());
                                pass.drawIndexed(Math.toIntExact(this.batchStartOffset / this.stride), 0, indexCount, 1);
                                submittedDraw = true;
                            }
                        }
                        default -> {
                            pass.draw(Math.toIntExact(this.batchStartOffset / this.stride), this.vertexCount);
                            submittedDraw = true;
                        }
                    }
                }
            } finally {
                if (this.ringBuffer.isMapped()) {
                    this.ringBuffer.unmap();
                }

                if (submittedDraw) {
                    this.frameUsed = true;
                    this.frameOffset = completedOffset;
                }

                this.building = false;
                this.currentOffset = this.frameOffset;
                this.batchStartOffset = this.frameOffset;
                this.vertexCount = 0;
                this.vertexBaseAddr = 0L;
                this.pipeline = null;
                this.texture = null;
            }
        }

        private void endFrame() {
            if (this.ringBuffer.isMapped()) {
                this.ringBuffer.unmap();
            }

            if (this.frameUsed) {
                this.ringBuffer.rotate();
            }

            this.frameUsed = false;
            this.frameOffset = 0L;
            this.currentOffset = 0L;
            this.batchStartOffset = 0L;
        }

        private static byte packNormal(float value) {
            float clamped = Mth.clamp(value, -1.0f, 1.0f);
            return (byte) ((int) (clamped * 127.0f) & 0xFF);
        }
    }

}
