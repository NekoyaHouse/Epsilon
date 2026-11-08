package com.github.epsilon.graphics.buffer

import com.github.epsilon.graphics.LuminRenderPipelines
import com.github.epsilon.graphics.schedulers.render2d.Render2DDrawPlan
import com.github.epsilon.graphics.schedulers.render2d.Render2DStats
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.CommandEncoder
import com.mojang.renderpearl.api.commands.GpuFence
import com.mojang.renderpearl.api.device.GpuDevice
import com.mojang.renderpearl.api.pipeline.IndexType
import org.lwjgl.system.MemoryUtil
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
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
        val data = ByteArray(bytes.toInt())
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
    private var copies = 0
    private var failCopyAfter = Int.MAX_VALUE
    private var failUpload = false
    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(type: Class<T>, invoke: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> invoke(method.name, args ?: emptyArray()) } as T
    private val encoder = proxy(CommandEncoder::class.java) { name, args -> when (name) {
        "writeToBuffer" -> {
            check(!failUpload) { "模拟上传失败" }
            val target = args[0] as GpuBufferSlice
            (args[1] as ByteBuffer).duplicate().get((target.buffer() as Buffer).data, target.offset().toInt(), target.length().toInt())
            writes++; null
        }
        "copyToBuffer" -> {
            check(copies < failCopyAfter) { "模拟拷贝失败" }
            val source = args[0] as GpuBufferSlice; val target = args[1] as GpuBufferSlice
            check(source.buffer().usage() and GpuBuffer.USAGE_COPY_SRC != 0)
            check(target.buffer().usage() and GpuBuffer.USAGE_COPY_DST != 0)
            check(!source.buffer().isClosed() && !target.buffer().isClosed())
            assertEquals(source.length(), target.length())
            (source.buffer() as Buffer).data.copyInto((target.buffer() as Buffer).data, target.offset().toInt(),
                source.offset().toInt(), (source.offset() + source.length()).toInt())
            copies++; null
        }
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

    @Test fun geometryCompactionPreservesDataAndWaitsForFenceBeforeFreeingOriginals() {
        val stats = Render2DStats()
        val arena = GeometryArena(stats, budget = 1024 * 1024, device = { device })
        val data = MemoryUtil.memAlloc(40_000).apply { for (i in 0 until capacity()) put(i, (i * 31).toByte()) }
        try {
            val handles = List(2) { arena.uploadGeometry(data, 16) }
            val originals = handles.map { it.slice }
            val generations = handles.map { it.generation }
            assertNotSame(originals[0].buffer(), originals[1].buffer())
            arena.compactGeometry()
            assertSame(handles[0].slice.buffer(), handles[1].slice.buffer())
            assertEquals(1L, stats.geometryCompactions)
            assertEquals(80_000L, stats.geometryCopiedBytes)
            assertEquals(160_000L, stats.liveBytes)
            handles.forEachIndexed { index, handle ->
                assertNotEquals(generations[index], handle.generation)
                val slice = handle.slice
                assertEquals(0L, slice.offset() % 16)
                val copied = (slice.buffer() as Buffer).data.copyOfRange(slice.offset().toInt(), (slice.offset() + slice.length()).toInt())
                assertContentEquals((originals[index].buffer() as Buffer).data.copyOfRange(0, 40_000), copied)
            }
            repeat(10) { arena.compactGeometry() }
            assertEquals(2, copies)
            assertEquals(2, writes)
            arena.seal()
            arena.collect()
            assertTrue(originals.all { !it.buffer().isClosed() })
            fences.last().completed = true
            arena.collect()
            assertEquals(80_000L, stats.liveBytes)
            assertTrue(originals.all { it.buffer().isClosed() })
            handles.forEach(arena::retire)
            arena.seal()
            fences.last().completed = true
            arena.collect()
            assertEquals(0L, stats.liveBytes)
            assertEquals(0L, stats.residentBytes)
        } finally { arena.close(); MemoryUtil.memFree(data) }
    }

    @Test fun capturedAlternatingPagePatternCollapsesFromFiveShapeRunsToOne() {
        val stats = Render2DStats()
        val arena = GeometryArena(stats, device = { device })
        val data = MemoryUtil.memAlloc(39_936) // 768 个 52 字节顶点，强制分配到两个 64 KiB 页。
        try {
            val a = arena.uploadGeometry(data, 52); val b = arena.uploadGeometry(data, 52)
            val index = Buffer(4096, GpuBuffer.USAGE_INDEX)
            // Nsight 1336 / 1347 / 1349 / 1376 / 1380 的实际页切换和范围数量。
            val order = List(9) { a } + b + List(25) { a } + List(2) { b } + List(13) { a }
            fun packets() = order.mapIndexed { i, handle ->
                val slice = handle.slice
                Render2DDrawPlan.Packet(Render2DDrawPlan.Binding(LuminRenderPipelines.UI_SHAPE, slice.buffer(), index,
                    IndexType.SHORT, null, emptyMap(), emptyMap()),
                    Render2DDrawPlan.Range(6, 0, Math.toIntExact(slice.offset() / 52) + i * 4, i))
            }
            assertEquals(5, Render2DDrawPlan.build(packets()).runs.size)
            arena.compactGeometry()
            val plan = Render2DDrawPlan.build(packets())
            assertEquals(1, plan.runs.size)
            assertEquals(50, plan.runs.single().ranges.size)
            assertEquals((0 until 50).toList(), plan.properties.map { it.source })
            assertEquals(packets().map { it.range.baseVertex }, plan.runs.single().ranges.map { it.baseVertex })
        } finally { arena.close(); MemoryUtil.memFree(data) }
    }

    @Test fun geometryAndDrawDataStaySeparateEvenWithTheSameStride() {
        val stats = Render2DStats()
        val arena = GeometryArena(stats, device = { device })
        val data = MemoryUtil.memAlloc(40_000)
        try {
            val drawData = List(2) { arena.upload(data, 16) }
            val slices = drawData.map { it.slice }
            val geometry = List(2) { arena.uploadGeometry(data, 16) }
            arena.compactGeometry()
            assertEquals(slices, drawData.map { it.slice })
            assertSame(geometry[0].slice.buffer(), geometry[1].slice.buffer())
            assertTrue(slices.none { it.buffer() === geometry[0].slice.buffer() })
            assertEquals(2, copies)
        } finally { arena.close(); MemoryUtil.memFree(data) }
    }

    @Test fun compactionCombinesAdjacentCopiesWithoutCopyingRetiredHoles() {
        val stats = Render2DStats()
        val arena = GeometryArena(stats, device = { device })
        val data = MemoryUtil.memAlloc(8_000)
        try {
            val handles = List(16) { i -> data.put(0, i.toByte()); arena.uploadGeometry(data, 16) }
            arena.retire(handles[2]); arena.seal()
            fences.single().completed = true
            arena.collect()
            arena.compactGeometry()
            assertEquals(3, copies)
            assertEquals(120_000L, stats.geometryCopiedBytes)
            handles.filterIndexed { i, _ -> i != 2 }.forEachIndexed { i, handle ->
                val expected = if (i < 2) i else i + 1
                assertEquals(expected.toByte(), (handle.slice.buffer() as Buffer).data[handle.slice.offset().toInt()])
            }
            assertEquals(16, writes)
        } finally { arena.close(); MemoryUtil.memFree(data) }
    }

    @Test fun growthReusesHeadroomAndRetiresSuccessiveLocationsBehindTheirOwnFences() {
        val stats = Render2DStats()
        val arena = GeometryArena(stats, device = { device })
        val data = MemoryUtil.memAlloc(40_000)
        try {
            val handles = MutableList(2) { arena.uploadGeometry(data, 16) }
            val originals = handles.map { it.slice.buffer() }
            arena.compactGeometry(); arena.seal()
            val middle = handles.first().slice.buffer()
            repeat(2) { handles.add(arena.uploadGeometry(data, 16)) }
            assertTrue(handles.all { it.slice.buffer() === middle })
            arena.compactGeometry()
            assertEquals(1L, stats.geometryCompactions)
            handles.add(arena.uploadGeometry(data, 16))
            arena.compactGeometry(); arena.seal()
            assertEquals(2L, stats.geometryCompactions)
            assertEquals(4, copies)
            assertTrue(handles.all { it.slice.buffer() === handles.first().slice.buffer() })
            fences.first().completed = true
            arena.collect()
            assertTrue(originals.all { it.isClosed() })
            assertFalse(middle.isClosed())
            fences.last().completed = true
            arena.collect()
            assertTrue(middle.isClosed())
            assertEquals(200_000L, stats.liveBytes)
            assertEquals(5, writes)
            handles.forEach(arena::retire); arena.seal()
            fences.last().completed = true
            arena.collect()
            assertEquals(0L, stats.liveBytes)
            assertEquals(0L, stats.residentBytes)
        } finally { arena.close(); MemoryUtil.memFree(data) }
    }

    @Test fun compactionRetriesAfterInFlightDrawDataReleasesBudget() {
        val stats = Render2DStats()
        val arena = GeometryArena(stats, budget = 5 * 65536, device = { device })
        val data = MemoryUtil.memAlloc(65536)
        try {
            val handles = List(2) { arena.uploadGeometry(data.duplicate().limit(40_000), 16) }
            val drawData = List(2) { arena.upload(data, 16) }
            arena.compactGeometry()
            assertEquals(0, copies)
            drawData.forEach(arena::retire); arena.seal()
            fences.single().completed = true
            arena.compactGeometry()
            assertSame(handles[0].slice.buffer(), handles[1].slice.buffer())
            assertEquals(1L, stats.geometryCompactions)
            assertTrue(stats.residentBytes <= 5 * 65536)
        } finally { arena.close(); MemoryUtil.memFree(data) }
    }

    @Test fun insufficientCompactionHeadroomKeepsTheOriginalPagesWithoutWaiting() {
        val stats = Render2DStats()
        val arena = GeometryArena(stats, budget = 2 * 65536, device = { device })
        val data = MemoryUtil.memAlloc(40_000)
        try {
            val handles = List(2) { arena.uploadGeometry(data, 16) }
            val slices = handles.map { it.slice }
            arena.compactGeometry()
            assertEquals(slices, handles.map { it.slice })
            assertEquals(0, copies)
            assertEquals(0L, stats.geometryCompactions)
            assertEquals(131_072L, stats.residentBytes)
            assertTrue(fences.isEmpty())
        } finally { arena.close(); MemoryUtil.memFree(data) }
    }

    @Test fun failedCompactionKeepsOriginalGeometryAndQuarantinesItsTargetUntilFence() {
        val stats = Render2DStats()
        val arena = GeometryArena(stats, device = { device })
        val data = MemoryUtil.memAlloc(40_000)
        try {
            val handles = List(2) { arena.uploadGeometry(data, 16) }
            val slices = handles.map { it.slice }
            failCopyAfter = 1
            assertFailsWith<IllegalStateException> { arena.compactGeometry() }
            assertEquals(slices, handles.map { it.slice })
            assertEquals(160_000L, stats.liveBytes)
            val failedTarget = buffers.last()
            assertFalse(failedTarget.closed)
            arena.seal()
            arena.collect()
            assertFalse(failedTarget.closed)
            fences.last().completed = true
            arena.collect()
            assertTrue(failedTarget.closed)
            assertEquals(80_000L, stats.liveBytes)
            failCopyAfter = Int.MAX_VALUE
            arena.compactGeometry()
            assertSame(handles[0].slice.buffer(), handles[1].slice.buffer())
            assertEquals(1L, stats.geometryCompactions)
        } finally { arena.close(); MemoryUtil.memFree(data) }
    }

    @Test fun retainedRecordingResolvesItsNewSliceAfterCompaction() {
        val stats = Render2DStats()
        val arena = GeometryArena(stats, device = { device })
        val recordings = List(2) { GeometryArena.RecordingBuffer(arena, 64, 16) }
        try {
            recordings.forEachIndexed { i, recording ->
                recording.ensureCapacity(40_000)
                recording.map().putInt(0, i + 123)
                recording.unmap()
            }
            assertNotSame(recordings[0].slice().buffer(), recordings[1].slice().buffer())
            arena.compactGeometry()
            assertSame(recordings[0].slice().buffer(), recordings[1].slice().buffer())
            recordings.forEachIndexed { i, recording ->
                val slice = recording.slice()
                val actual = ByteBuffer.wrap((slice.buffer() as Buffer).data).order(java.nio.ByteOrder.nativeOrder()).getInt(slice.offset().toInt())
                assertEquals(i + 123, actual)
            }
            assertEquals(2, writes)
        } finally { recordings.forEach { it.reset() }; arena.close() }
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
