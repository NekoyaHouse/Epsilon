package com.github.epsilon.graphics.buffer

import com.github.epsilon.graphics.schedulers.render2d.Render2DStats
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.GpuFence
import com.mojang.renderpearl.api.device.GpuDevice
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.TreeMap

/** 不覆盖已提交的分配；只有 fence 完成后空洞才重新进入分配器。 */
class GeometryArena(private val stats: Render2DStats, private val budget: Long = 64L * 1024 * 1024,
                    private val device: () -> GpuDevice = RenderSystem::getDevice) : AutoCloseable {
    private class Page(val buffer: GpuBuffer, val allocator: RangeAllocator, val stride: Int)
    class Handle internal constructor(val slice: GpuBufferSlice, val generation: Long, internal val release: () -> Unit)
    private data class Retirement(val fence: GpuFence, val handles: List<Handle>)
    private val pages = ArrayList<Page>()
    private val retired = ArrayDeque<Retirement>()
    private val pending = ArrayList<Handle>()
    private var generation = 0L
    private var closed = false

    fun upload(data: ByteBuffer, stride: Int): Handle {
        check(!closed)
        collect()
        val bytes = data.remaining()
        var page: Page? = null
        var offset = -1
        for (candidate in pages) {
            if (candidate.stride != stride) continue
            offset = candidate.allocator.allocate(bytes)
            if (offset >= 0) { page = candidate; break }
        }
        if (page == null) {
            val capacity = ((maxOf(64 * 1024, bytes) + stride - 1) / stride) * stride
            // fence 可能属于尚未提交的 Vulkan command buffer，不能在渲染线程阻塞等待。
            check(stats.residentBytes + capacity <= budget) { "Lumin geometry budget exceeded: $bytes bytes requested" }
            val buffer = device().createBuffer({ "Lumin Geometry Page" },
                GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_INDIRECT_PARAMETERS or GpuBuffer.USAGE_COPY_DST, capacity.toLong())
            page = Page(buffer, RangeAllocator(capacity), stride)
            pages.add(page)
            stats.residentBytes += capacity
            stats.peakBytes = maxOf(stats.peakBytes, stats.residentBytes)
            offset = page.allocator.allocate(bytes)
        }
        val selected = page
        val slice = selected.buffer.slice(offset.toLong(), bytes.toLong())
        val uploadStart = System.nanoTime()
        try { device().createCommandEncoder().writeToBuffer(slice, data) }
        catch (failure: Throwable) { selected.allocator.free(offset, bytes); throw failure }
        finally { stats.uploadNanos += System.nanoTime() - uploadStart }
        stats.uploadedBytes += bytes
        stats.liveBytes += bytes
        return Handle(slice, ++generation) {
            selected.allocator.free(offset, bytes)
            stats.liveBytes -= bytes
        }
    }

    fun retire(handle: Handle) { if (!closed) pending.add(handle) }

    /** 在所有 pass 关闭后调用；pending 可能仍被当前提交引用。 */
    fun seal() {
        if (pending.isEmpty()) return
        retired.addLast(Retirement(device().createCommandEncoder().createFence(), pending.toList()))
        pending.clear()
    }

    fun collect() {
        while (retired.isNotEmpty() && retired.first().fence.awaitCompletion(0)) {
            val item = retired.removeFirst()
            item.handles.forEach { it.release() }
            item.fence.close()
        }
        val iterator = pages.iterator()
        while (iterator.hasNext()) {
            val page = iterator.next()
            if (page.allocator.isEmpty) {
                stats.residentBytes -= page.buffer.size()
                page.buffer.close()
                iterator.remove()
            }
        }
    }

    fun <T> recording(block: () -> T): T {
        val previous = current
        current = this
        return try { block() } finally { current = previous }
    }

    override fun close() {
        if (closed) return
        closed = true
        pending.clear()
        for (item in retired) {
            item.fence.close()
        }
        retired.clear()
        // 当前版本的 GPU buffer.close 由后端延迟销毁，无需等待当前 submit 的 fence。
        pages.forEach { it.buffer.close() }
        pages.clear()
        stats.liveBytes = 0
        stats.residentBytes = 0
    }

    /** 编译作用域仅在渲染线程使用，普通 renderer 的环形缓冲不受影响。 */
    companion object { internal var current: GeometryArena? = null; private set }

    internal class RecordingBuffer(private val arena: GeometryArena, initialSize: Int, private val stride: Int) {
        private var data: ByteBuffer? = null
        private var capacity = ((minOf(initialSize, 256) + stride - 1) / stride) * stride
        private var handle: Handle? = null
        private var used = 0
        fun size() = capacity
        fun ensureCapacity(bytes: Int) {
            used = maxOf(used, bytes)
            if (bytes <= capacity) return
            check(handle == null) { "已提交的几何必须使用新分配" }
            capacity = ((maxOf(bytes, capacity * 2) + stride - 1) / stride) * stride
            data = data?.let { MemoryUtil.memRealloc(it, capacity) }
        }
        fun map(): ByteBuffer {
            check(handle == null)
            return data ?: MemoryUtil.memAlloc(capacity).also { data = it }
        }
        fun unmap() {
            val source = data ?: return
            val upload = source.duplicate().position(0).limit(((used + stride - 1) / stride) * stride)
            // 上传失败保留 CPU 副本供后续帧重试，不能留下永远没有句柄的缓存。
            handle = arena.upload(upload, stride)
            MemoryUtil.memFree(source)
            data = null
        }
        fun slice(): GpuBufferSlice { unmap(); return checkNotNull(handle).slice }
        fun reset() {
            data?.let { MemoryUtil.memFree(it) }; data = null
            handle?.let { arena.retire(it) }; handle = null; used = 0
        }
    }
}

/** 地址有序的空闲范围，合并相邻空洞，避免组件反复增删造成永久碎片。 */
internal class RangeAllocator(private val capacity: Int) {
    private val free = TreeMap<Int, Int>().apply { put(0, capacity) }
    private val allocated = HashMap<Int, Int>()
    val isEmpty get() = free.size == 1 && free[0] == capacity
    fun allocate(bytes: Int): Int {
        require(bytes > 0)
        val entry = free.entries.firstOrNull { it.value >= bytes } ?: return -1
        val offset = entry.key
        val remaining = entry.value - bytes
        free.remove(offset)
        if (remaining > 0) free[offset + bytes] = remaining
        allocated[offset] = bytes
        return offset
    }
    fun free(offset: Int, bytes: Int) {
        check(allocated.remove(offset) == bytes) { "无效或重复的几何范围释放" }
        var start = offset
        var size = bytes
        free.lowerEntry(start)?.takeIf { it.key + it.value == start }?.let {
            start = it.key; size += it.value; free.remove(it.key)
        }
        free.ceilingEntry(start)?.takeIf { start + size == it.key }?.let {
            size += it.value; free.remove(it.key)
        }
        free[start] = size
    }
}
