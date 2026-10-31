package com.github.epsilon.graphics.buffer

import com.github.epsilon.graphics.LuminRenderSystem
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.CommandEncoder
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

/**
 * Lumin 的环形 GPU 缓冲区。
 *
 * 默认维护 3 个轮转槽位，用来避免同一帧内写入和读取冲突。
 * 当当前槽位容量不足时，会自动扩展当前槽位的字节容量，并尽量保留已写入数据。
 *
 * 该类只负责缓冲区管理，不负责具体顶点格式或绘制逻辑。
 */
open class LuminRingBuffer(size: Long, usage: @GpuBuffer.Usage Int) {

    private val usage: Int
    private val buffers = arrayOfNulls<GpuBuffer>(BUFFER_COUNT)
    private val sizes = IntArray(BUFFER_COUNT)
    private val retiredBuffers = ArrayList<GpuBuffer>()

    private var mappedBuffer: GpuBufferSlice.MappedView? = null
    private var current = 0
    private var mapped = false
    private var frameId = Long.MIN_VALUE

    init {
        val initialSize = checkedBufferSize(size)
        this.usage = GpuBuffer.USAGE_MAP_WRITE or GpuBuffer.USAGE_COPY_DST or GpuBuffer.USAGE_COPY_SRC or usage
        for (i in buffers.indices) {
            buffers[i] = createBuffer(i, initialSize)
            sizes[i] = initialSize
        }
    }

    /**
     * 获取当前槽位的容量。
     *
     * @return 当前 GPU 缓冲区大小，单位字节
     */
    fun size(): Int = sizes[current]

    /**
     * 判断当前槽位是否已经映射到 CPU 可写内存。
     *
     * @return 已映射返回 true
     */
    fun isMapped(): Boolean = mapped

    /**
     * 获取当前映射视图。
     *
     * @return 当前映射的 ByteBuffer
     * @throws IllegalStateException 当缓冲区尚未映射时抛出
     */
    fun getMappedBuffer(): ByteBuffer {
        val buffer = mappedBuffer ?: throw IllegalStateException("LuminRingBuffer is not mapped")
        return buffer.data()
    }

    /**
     * 确保当前槽位拥有足够容量。
     *
     * 若容量不足，会扩展当前槽位并尽量保留已有内容。
     *
     * @param requiredBytes 所需的最小字节数
     */
    fun ensureCapacity(requiredBytes: Long) {
        if (requiredBytes <= size()) {
            return
        }

        resizeCurrent(growSize(size(), requiredBytes))
    }

    /**
     * 将当前槽位映射为可写内存。
     *
     * 如果已经映射，则直接返回。
     */
    fun tryMap() {
        if (mapped) return
        beginFrameIfNeeded()
        mappedBuffer = getGpuBuffer().map(false, true)
        mapped = true
    }

    /**
     * 取消当前槽位映射。
     */
    fun unmap() {
        if (!mapped) return
        mappedBuffer!!.close()
        mappedBuffer = null
        mapped = false
    }

    /**
     * 切换到下一个轮转槽位。
     */
    fun rotate() {
        beginFrameIfNeeded()
        current = (current + 1) % buffers.size
    }

    /**
     * 取消映射当前槽位并切换到下一个轮转槽位。
     *
     * @return 切换前的 GPU 缓冲区
     */
    fun unmapAndRotate(): GpuBuffer {
        val lastGpuBuffer = getGpuBuffer()
        unmap()
        rotate()
        return lastGpuBuffer
    }

    /**
     * 获取当前槽位对应的 GPU 缓冲区。
     *
     * @return 当前 GPU 缓冲区
     */
    fun getGpuBuffer(): GpuBuffer = buffers[current]!!

    /**
     * 将源数据写入当前槽位的指定偏移。
     *
     * @param commandEncoder 命令编码器
     * @param offset         写入偏移，单位字节
     * @param source         待写入数据
     */
    fun write(commandEncoder: CommandEncoder, offset: Long, source: ByteBuffer) {
        ensureCapacity(offset + source.remaining())
        commandEncoder.writeToBuffer(getGpuBuffer().slice(offset, source.remaining().toLong()), source)
    }

    /**
     * 关闭所有 GPU 资源。
     */
    fun close() {
        if (mapped) unmap()
        for (buffer in buffers) {
            buffer!!.close()
        }
        closeRetiredBuffers()
    }

    private fun resizeCurrent(nextSize: Int) {
        val oldSize = sizes[current]
        if (nextSize <= oldSize) {
            return
        }

        var preservedMappedData: ByteBuffer? = null
        val preservedBytes = minOf(oldSize, nextSize)
        if (mapped && preservedBytes > 0) {
            val source = mappedBuffer!!.data()
            preservedMappedData = MemoryUtil.memAlloc(preservedBytes)
            MemoryUtil.memCopy(
                MemoryUtil.memAddress(source),
                MemoryUtil.memAddress(preservedMappedData),
                preservedBytes.toLong()
            )
        }

        if (mapped) {
            unmap()
        }

        val oldBuffer = buffers[current]!!
        val nextBuffer = createBuffer(current, nextSize)
        buffers[current] = nextBuffer
        sizes[current] = nextSize

        val preservedData = preservedMappedData
        if (preservedData != null) {
            try {
                tryMap()
                MemoryUtil.memCopy(
                    MemoryUtil.memAddress(preservedData),
                    MemoryUtil.memAddress(mappedBuffer!!.data()),
                    preservedBytes.toLong()
                )
            } finally {
                MemoryUtil.memFree(preservedData)
            }
        } else if (preservedBytes > 0) {
            RenderSystem.getDevice().createCommandEncoder()
                .copyToBuffer(oldBuffer.slice(0, preservedBytes.toLong()), nextBuffer.slice(0, preservedBytes.toLong()))
        }

        retiredBuffers.add(oldBuffer)
    }

    private fun beginFrameIfNeeded() {
        val currentFrameId = LuminRenderSystem.getRenderFrameId()
        if (frameId == currentFrameId) {
            return
        }

        frameId = currentFrameId
        closeRetiredBuffers()
    }

    private fun closeRetiredBuffers() {
        if (retiredBuffers.isEmpty()) {
            return
        }

        for (buffer in retiredBuffers) {
            buffer.close()
        }
        retiredBuffers.clear()
    }

    private fun createBuffer(index: Int, size: Int): GpuBuffer =
        RenderSystem.getDevice().createBuffer({ "lumin-ring-buffer #$index" }, usage, size.toLong())

    companion object {

        private const val BUFFER_COUNT = 3

        private fun checkedBufferSize(size: Long): Int {
            val checkedSize = Math.toIntExact(size)
            if (checkedSize <= 0) {
                throw IllegalArgumentException("size must be positive")
            }

            return checkedSize
        }

        private fun growSize(currentSize: Int, requiredBytes: Long): Int {
            val requiredSize = checkedBufferSize(requiredBytes)
            var nextSize = currentSize
            while (requiredSize > nextSize) {
                nextSize = Math.multiplyExact(nextSize, 2)
            }

            return nextSize
        }
    }
}
