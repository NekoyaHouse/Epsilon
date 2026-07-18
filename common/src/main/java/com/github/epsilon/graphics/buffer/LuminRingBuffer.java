package com.github.epsilon.graphics.buffer;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Lumin 的环形 GPU 缓冲区。
 *
 * <p>默认维护 3 个轮转槽位，用来避免同一帧内写入和读取冲突。
 * 当当前槽位容量不足时，会自动扩展当前槽位的字节容量，并尽量保留已写入数据。
 *
 * <p>该类只负责缓冲区管理，不负责具体顶点格式或绘制逻辑。
 */
public class LuminRingBuffer {

    private static final int BUFFER_COUNT = 3;

    private final int usage;
    private final GpuBuffer[] buffers = new GpuBuffer[BUFFER_COUNT];
    private final int[] sizes = new int[BUFFER_COUNT];
    private final List<GpuBuffer> retiredBuffers = new ArrayList<>();

    private GpuBuffer.MappedView mappedBuffer;
    private int current;
    private boolean mapped;
    private long frameId = Long.MIN_VALUE;

    /**
     * 创建一个新的环形缓冲区。
     *
     * @param size  初始字节容量
     * @param usage 额外的 GPU 使用标记
     */
    public LuminRingBuffer(long size, @GpuBuffer.Usage int usage) {
        int initialSize = checkedBufferSize(size);
        this.usage = GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC | usage;
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = createBuffer(i, initialSize);
            sizes[i] = initialSize;
        }
    }

    /**
     * 获取当前槽位的容量。
     *
     * @return 当前 GPU 缓冲区大小，单位字节
     */
    public int size() {
        return sizes[current];
    }

    /**
     * 判断当前槽位是否已经映射到 CPU 可写内存。
     *
     * @return 已映射返回 true
     */
    public boolean isMapped() {
        return mapped;
    }

    /**
     * 获取当前映射视图。
     *
     * @return 当前映射的 ByteBuffer
     * @throws IllegalStateException 当缓冲区尚未映射时抛出
     */
    public ByteBuffer getMappedBuffer() {
        if (mappedBuffer == null) {
            throw new IllegalStateException("LuminRingBuffer is not mapped");
        }
        return mappedBuffer.data();
    }

    /**
     * 确保当前槽位拥有足够容量。
     *
     * <p>若容量不足，会扩展当前槽位并尽量保留已有内容。
     *
     * @param requiredBytes 所需的最小字节数
     */
    public void ensureCapacity(long requiredBytes) {
        if (requiredBytes <= size()) {
            return;
        }

        resizeCurrent(growSize(size(), requiredBytes));
    }

    /**
     * 将当前槽位映射为可写内存。
     *
     * <p>如果已经映射，则直接返回。
     */
    public void tryMap() {
        if (mapped) return;
        beginFrameIfNeeded();
        mappedBuffer = RenderSystem.getDevice().createCommandEncoder().mapBuffer(getGpuBuffer(), false, true);
        mapped = true;
    }

    /**
     * 取消当前槽位映射。
     */
    public void unmap() {
        if (!mapped) return;
        mappedBuffer.close();
        mappedBuffer = null;
        mapped = false;
    }

    /**
     * 切换到下一个轮转槽位。
     */
    public void rotate() {
        beginFrameIfNeeded();
        current = (current + 1) % buffers.length;
    }

    /**
     * 取消映射当前槽位并切换到下一个轮转槽位。
     *
     * @return 切换前的 GPU 缓冲区
     */
    public GpuBuffer unmapAndRotate() {
        GpuBuffer lastGpuBuffer = getGpuBuffer();
        unmap();
        rotate();
        return lastGpuBuffer;
    }

    /**
     * 获取当前槽位对应的 GPU 缓冲区。
     *
     * @return 当前 GPU 缓冲区
     */
    public GpuBuffer getGpuBuffer() {
        return buffers[current];
    }

    /**
     * 将源数据写入当前槽位的指定偏移。
     *
     * @param commandEncoder 命令编码器
     * @param offset         写入偏移，单位字节
     * @param source         待写入数据
     */
    public void write(CommandEncoder commandEncoder, long offset, ByteBuffer source) {
        ensureCapacity(offset + source.remaining());
        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(getGpuBuffer().slice((int) offset, source.remaining()), false, true)) {
            MemoryUtil.memCopy(source, mappedView.data());
        }
    }

    /**
     * 关闭所有 GPU 资源。
     */
    public void close() {
        if (mapped) unmap();
        for (GpuBuffer buffer : buffers) {
            buffer.close();
        }
        closeRetiredBuffers();
    }

    private void resizeCurrent(int nextSize) {
        int oldSize = sizes[current];
        if (nextSize <= oldSize) {
            return;
        }

        ByteBuffer preservedMappedData = null;
        int preservedBytes = Math.min(oldSize, nextSize);
        if (mapped && preservedBytes > 0) {
            ByteBuffer source = mappedBuffer.data();
            preservedMappedData = MemoryUtil.memAlloc(preservedBytes);
            MemoryUtil.memCopy(MemoryUtil.memAddress(source), MemoryUtil.memAddress(preservedMappedData), preservedBytes);
        }

        if (mapped) {
            unmap();
        }

        GpuBuffer oldBuffer = buffers[current];
        GpuBuffer nextBuffer = createBuffer(current, nextSize);
        buffers[current] = nextBuffer;
        sizes[current] = nextSize;

        if (preservedMappedData != null) {
            try {
                tryMap();
                MemoryUtil.memCopy(MemoryUtil.memAddress(preservedMappedData), MemoryUtil.memAddress(mappedBuffer.data()), preservedBytes);
            } finally {
                MemoryUtil.memFree(preservedMappedData);
            }
        } else if (preservedBytes > 0) {
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .copyToBuffer(oldBuffer.slice(0, preservedBytes), nextBuffer.slice(0, preservedBytes));
        }

        retiredBuffers.add(oldBuffer);
    }

    private void beginFrameIfNeeded() {
        long currentFrameId = LuminRenderSystem.getRenderFrameId();
        if (frameId == currentFrameId) {
            return;
        }

        frameId = currentFrameId;
        closeRetiredBuffers();
    }

    private void closeRetiredBuffers() {
        if (retiredBuffers.isEmpty()) {
            return;
        }

        retiredBuffers.forEach(GpuBuffer::close);
        retiredBuffers.clear();
    }

    private static int checkedBufferSize(long size) {
        int checkedSize = Math.toIntExact(size);
        if (checkedSize <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }

        return checkedSize;
    }

    private static int growSize(int currentSize, long requiredBytes) {
        int requiredSize = checkedBufferSize(requiredBytes);
        int nextSize = currentSize;
        while (requiredSize > nextSize) {
            nextSize = Math.multiplyExact(nextSize, 2);
        }

        return nextSize;
    }

    private GpuBuffer createBuffer(int index, int size) {
        return RenderSystem.getDevice().createBuffer(() -> "lumin-ring-buffer #" + index, usage, size);
    }

}
