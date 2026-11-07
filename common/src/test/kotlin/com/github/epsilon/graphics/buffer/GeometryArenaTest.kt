package com.github.epsilon.graphics.buffer

import com.github.epsilon.graphics.schedulers.render2d.Render2DStats
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.CommandEncoder
import com.mojang.renderpearl.api.commands.GpuFence
import com.mojang.renderpearl.api.device.GpuDevice
import org.lwjgl.system.MemoryUtil
import java.lang.reflect.Proxy
import kotlin.test.*

class GeometryArenaTest {
    private class Fence : GpuFence {
        var completed = false
        var closed = false
        override fun awaitCompletion(timeoutNS: Long): Boolean {
            check(timeoutNS == 0L) { "尚未提交的 Vulkan fence 不允许阻塞等待" }
            return completed
        }
        override fun close() { closed = true }
    }
    private class Buffer(private val bytes: Long, private val flags: Int) : GpuBuffer {
        var closed = false
        override fun size() = bytes
        override fun usage() = flags
        override fun isClosed() = closed
        override fun close() { check(!closed); closed = true }
        override fun map(offset: Long, length: Long, read: Boolean, write: Boolean): GpuBufferSlice.MappedView = error("不应映射持久页")
    }
    private val fences = ArrayList<Fence>()
    private val buffers = ArrayList<Buffer>()
    private var writes = 0
    private var failUpload = false
    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(type: Class<T>, invoke: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> invoke(method.name, args ?: emptyArray()) } as T
    private val encoder = proxy(CommandEncoder::class.java) { name, _ -> when (name) {
        "writeToBuffer" -> { check(!failUpload) { "模拟上传失败" }; writes++; null }
        "createFence" -> Fence().also { fences.add(it) }
        else -> error(name)
    } }
    private val device = proxy(GpuDevice::class.java) { name, args -> when (name) {
        "createBuffer" -> Buffer(args[2] as Long, args[1] as Int).also { buffers.add(it) }
        "createCommandEncoder" -> encoder
        else -> error(name)
    } }

    @Test fun rangesCoalesceAndRejectDoubleFree() {
        val ranges = RangeAllocator(100)
        assertEquals(0, ranges.allocate(20))
        assertEquals(20, ranges.allocate(30))
        ranges.free(0, 20)
        assertEquals(-1, ranges.allocate(60))
        ranges.free(20, 30)
        assertTrue(ranges.isEmpty)
        assertFailsWith<IllegalStateException> { ranges.free(20, 30) }
        assertEquals(0, ranges.allocate(100))
    }

    @Test fun submittedRangesAreNotReusedUntilFenceCompletes() {
        val stats = Render2DStats()
        val arena = GeometryArena(stats, device = { device })
        val data = MemoryUtil.memAlloc(48)
        try {
            val first = arena.upload(data, 16)
            assertEquals(48, stats.liveBytes)
            arena.retire(first)
            arena.seal()
            arena.collect()
            assertEquals(48, stats.liveBytes)
            val second = arena.upload(data, 16)
            assertNotEquals(first.slice.offset(), second.slice.offset())
            fences.single().completed = true
            arena.collect()
            val third = arena.upload(data, 16)
            assertEquals(first.slice.offset(), third.slice.offset())
            assertNotEquals(first.generation, third.generation)
            assertEquals(3, writes)
            arena.retire(second); arena.retire(third); arena.seal()
        } finally { arena.close(); MemoryUtil.memFree(data) }
        assertTrue(buffers.all { it.closed })
        assertTrue(fences.all { it.closed })
        assertEquals(0, stats.residentBytes)
    }

    @Test fun repeatedPageLifecyclesRemainBounded() {
        val stats = Render2DStats()
        val arena = GeometryArena(stats, device = { device })
        val data = MemoryUtil.memAlloc(64)
        try {
            repeat(100) {
                val handle = arena.upload(data, 16)
                arena.retire(handle); arena.seal()
                fences.last().completed = true
                arena.collect()
                assertEquals(0, stats.residentBytes)
                assertEquals(0, stats.liveBytes)
            }
            assertEquals(65536, stats.peakBytes)
        } finally { arena.close(); MemoryUtil.memFree(data) }
    }

    @Test fun closeDoesNotWaitForCurrentSubmitOrCreateAnotherFence() {
        val stats = Render2DStats()
        val arena = GeometryArena(stats, device = { device })
        val data = MemoryUtil.memAlloc(48)
        try {
            arena.retire(arena.upload(data, 16))
            arena.seal()
            arena.retire(arena.upload(data, 16))
            arena.close()
            arena.close()
            assertEquals(1, fences.size)
            assertTrue(fences.single().closed)
            assertFalse(fences.single().completed)
            assertTrue(buffers.all { it.closed })
            assertEquals(0, stats.liveBytes)
            assertEquals(0, stats.residentBytes)
        } finally { arena.close(); MemoryUtil.memFree(data) }
    }

    @Test fun budgetExhaustionDoesNotBlockOnCurrentSubmit() {
        val stats = Render2DStats()
        val arena = GeometryArena(stats, budget = 65536, device = { device })
        val data = MemoryUtil.memAlloc(65536)
        try {
            arena.retire(arena.upload(data, 16))
            arena.seal()
            assertFailsWith<IllegalStateException> { arena.upload(data, 16) }
            assertEquals(65536, stats.residentBytes)
            fences.single().completed = true
            arena.collect()
            arena.retire(arena.upload(data, 16))
        } finally { arena.close(); MemoryUtil.memFree(data) }
    }

    @Test fun failedUploadRetainsRecordingForRetry() {
        val stats = Render2DStats()
        val arena = GeometryArena(stats, device = { device })
        val recording = GeometryArena.RecordingBuffer(arena, 64, 16)
        try {
            recording.ensureCapacity(32)
            recording.map().putInt(0, 123)
            failUpload = true
            assertFailsWith<IllegalStateException> { recording.unmap() }
            assertEquals(0, stats.liveBytes)
            assertEquals(123, recording.map().getInt(0))
            failUpload = false
            recording.unmap()
            assertEquals(32, recording.slice().length())
            assertEquals(32, stats.liveBytes)
            assertEquals(1, writes)
        } finally { recording.reset(); arena.close() }
    }
}
