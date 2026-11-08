package com.github.epsilon.graphics.schedulers.render2d

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.buffer.GeometryArena
import com.github.epsilon.graphics.schedulers.render2d.Render2DDrawPlan.Binding
import com.github.epsilon.graphics.schedulers.render2d.Render2DDrawPlan.Packet
import com.github.epsilon.graphics.schedulers.render2d.Render2DDrawPlan.Range
import com.github.epsilon.graphics.schedulers.render2d.Render2DDrawPlan.Texture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.*
import com.mojang.renderpearl.api.pipeline.*
import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTextureView
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.util.Optional
import java.util.OptionalDouble
import java.util.function.Supplier

/** 先完成准备和上传，再打开真实 pass；录制 pass 不持有 GPU 命令编码器。 */
internal class Render2DExecutor(private val arena: GeometryArena, private val stats: Render2DStats) : AutoCloseable {
    data class Draw(val pipeline: RenderPipeline, val properties: Render2DProperties,
                    val prepare: () -> Boolean, val submit: (RenderPass) -> Unit)
    private data class Instance(val properties: Render2DProperties, val textureSlot: Int)
    private var properties = emptyList<Instance>()
    private var propertyBuffer: GeometryArena.Handle? = null
    private var packets = emptyList<Packet>()
    private var plan = Render2DDrawPlan.Plan(emptyList(), emptyList())
    private val indirect = LinkedHashMap<List<Range>, GeometryArena.Handle>(32, 0.75f, true)
    /** 可关闭 GPU 时间查询；读取结果从不等待 GPU。 */
    var measureGpu = false
    private val queries = ArrayDeque<GpuQueryPool>()

    fun execute(draws: List<Draw>) = LuminRenderSystem.withSharedGuiTransform { executePrepared(draws) }

    private fun executePrepared(draws: List<Draw>) {
        val color = LuminRenderSystem.resolveColorView() ?: return
        val resolved = draws.filter { it.prepare() }
        if (resolved.isEmpty()) return
        LuminRenderSystem.applyOrthoProjection()
        val originals = Render2DPipelines.sources.associateBy { RenderSystem.getCompiledPipeline(it) }
        val recorder = RecordingPass(originals)
        resolved.forEachIndexed { index, draw ->
            recorder.source = draw.pipeline
            recorder.property = index
            draw.submit(recorder)
        }
        if (packets != recorder.packets) {
            val start = System.nanoTime()
            packets = recorder.packets
            plan = Render2DDrawPlan.build(packets)
            stats.planBuilds++
            stats.planningNanos += System.nanoTime() - start
        }
        if (plan.runs.isEmpty()) return
        updateProperties(plan.properties.map { Instance(resolved[it.source].properties, it.textureSlot) })
        val features = RenderSystem.getDevice().deviceInfo.features()
        val useIndirect = features.multiDrawIndirect() && features.nonZeroFirstInstance()
        val prepared = plan.runs.map { run ->
            val compiled = RenderSystem.getCompiledPipeline(Render2DPipelines.variant(run.binding.pipeline))
            val commands = if (useIndirect && run.ranges.size > 1 && run.binding.indices != null) indirect(run.ranges) else null
            Triple(run, compiled, commands)
        }
        pollQueries()
        val query = if (measureGpu && queries.size < 4) RenderSystem.getDevice().createTimestampQueryPool(2) else null
        val transform = LuminRenderSystem.writeDefaultGuiTransform()
        try {
            RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                { "Lumin Ordered 2D" }, color, Optional.empty(), LuminRenderSystem.resolveDepthView(), OptionalDouble.empty()
            ).use { pass ->
                query?.let { pass.writeTimestamp(it, 0) }
                RenderSystem.bindDefaultUniforms(pass)
                pass.setUniform("DynamicTransforms", transform)
                if (features.nonZeroFirstInstance()) pass.setVertexBuffer(1, checkNotNull(propertyBuffer).slice)
                var previous: Binding? = null
                var indexBuffer: GpuBuffer? = null
                var indexType: IndexType? = null
                for ((run, pipeline, commands) in prepared) {
                    val binding = run.binding
                    val pipelineChanged = previous?.pipeline != binding.pipeline
                    if (pipelineChanged) pass.setPipeline(pipeline)
                    if (previous?.vertices != binding.vertices) pass.setVertexBuffer(0, binding.vertices.slice())
                    if (binding.indices != null && (indexBuffer != binding.indices || indexType != binding.indexType)) {
                        pass.setIndexBuffer(binding.indices, checkNotNull(binding.indexType))
                        indexBuffer = binding.indices
                        indexType = binding.indexType
                    }
                    if (previous == null || previous.clip != binding.clip) {
                        val clip = binding.clip
                        if (clip == null) pass.disableScissor()
                        else pass.enableScissor(clip.x, clip.y, clip.width, clip.height)
                    }
                    binding.textures.forEach { (name, texture) ->
                        if (pipelineChanged || previous.textures[name] != texture) pass.setUniform(name, texture.view, texture.sampler)
                    }
                    binding.uniforms.forEach { (name, buffer) ->
                        if (pipelineChanged || previous.uniforms[name] != buffer) pass.setUniform(name, buffer)
                    }
                    previous = binding
                    if (commands != null) {
                        pass.drawIndexedIndirect(commands.slice, run.ranges.size)
                        stats.drawCalls++
                    } else for ((count, first, baseVertex, property) in run.ranges) {
                        val instance = if (features.nonZeroFirstInstance()) property else 0
                        if (!features.nonZeroFirstInstance()) {
                            val slice = checkNotNull(propertyBuffer).slice
                            pass.setVertexBuffer(1, slice.buffer().slice(slice.offset() + property * 48L, 48))
                        }
                        if (binding.indices != null) pass.drawIndexed(count, 1, first, baseVertex, instance)
                        else pass.draw(count, 1, first, instance)
                        stats.drawCalls++
                    }
                    stats.drawRanges += run.ranges.size
                }
                query?.let { pass.writeTimestamp(it, 1) }
            }
            query?.let { queries.addLast(it) }
        } catch (failure: Throwable) { query?.close(); throw failure }
    }

    private fun updateProperties(next: List<Instance>) {
        if (next == properties && propertyBuffer != null) return
        val data = MemoryUtil.memAlloc(next.size * 48)
        try {
            for ((property, textureSlot) in next) {
                val t = property.transform
                data.putFloat(t.m00).putFloat(t.m01).putFloat(t.x).putFloat(textureSlot.toFloat())
                data.putFloat(t.m10).putFloat(t.m11).putFloat(t.y).putFloat(0f)
                data.putFloat(property.red).putFloat(property.green).putFloat(property.blue).putFloat(property.alpha)
            }
            data.flip()
            val replacement = arena.upload(data, 48)
            propertyBuffer?.let { arena.retire(it) }
            propertyBuffer = replacement
            properties = next
        } finally { MemoryUtil.memFree(data) }
    }

    private fun indirect(ranges: List<Range>): GeometryArena.Handle = indirect.getOrPut(ranges) {
        val data = MemoryUtil.memAlloc(ranges.size * 20)
        try {
            ranges.forEach { data.putInt(it.count).putInt(1).putInt(it.first).putInt(it.baseVertex).putInt(it.property) }
            data.flip()
            arena.upload(data, 20)
        } finally { MemoryUtil.memFree(data) }
    }.also {
        while (indirect.size > 128) {
            val first = indirect.entries.first()
            arena.retire(first.value); indirect.remove(first.key)
        }
    }

    private fun pollQueries() {
        while (queries.isNotEmpty()) {
            val query = queries.first()
            val start = query.getValue(0); val end = query.getValue(1)
            if (start.isEmpty || end.isEmpty) break
            stats.gpuNanos = ((end.asLong - start.asLong) * RenderSystem.getDevice().deviceInfo.timestampPeriod()).toLong()
            queries.removeFirst().close()
        }
    }
    fun resetCache() {
        propertyBuffer?.let { arena.retire(it) }; propertyBuffer = null
        properties = emptyList(); packets = emptyList(); plan = Render2DDrawPlan.Plan(emptyList(), emptyList())
        indirect.values.forEach { arena.retire(it) }; indirect.clear()
    }
    override fun close() { resetCache(); queries.forEach { it.close() }; queries.clear() }

    private class RecordingPass(private val originals: Map<CompiledRenderPipeline, RenderPipeline>) : AbstractRecordingPass() {
        lateinit var source: RenderPipeline
        var property = 0
        val packets = ArrayList<Packet>()
        private var vertices: GpuBufferSlice? = null
        private var indices: GpuBuffer? = null
        private var indexType: IndexType? = null
        private var clip: Render2DScissor? = null
        private val textures = HashMap<String, Texture>()
        private val uniforms = HashMap<String, GpuBufferSlice>()
        override fun setPipeline(pipeline: CompiledRenderPipeline) { source = originals.getValue(pipeline) }
        override fun setVertexBuffer(slot: Int, vertexBuffer: GpuBufferSlice?) { check(slot == 0); vertices = vertexBuffer }
        override fun setIndexBuffer(indexBuffer: GpuBuffer, indexType: IndexType) { indices = indexBuffer; this.indexType = indexType }
        override fun setUniform(name: String, textureView: GpuTextureView?, sampler: GpuSampler?) { textures[name] = Texture(textureView, sampler) }
        override fun setUniform(name: String, value: GpuBuffer) = setUniform(name, value.slice())
        override fun setUniform(name: String, value: GpuBufferSlice) { if (name != "DynamicTransforms") uniforms[name] = value }
        override fun enableScissor(x: Int, y: Int, width: Int, height: Int) { clip = Render2DScissor(x, y, width, height) }
        override fun disableScissor() { clip = null }
        private fun binding(indexed: Boolean): Binding {
            val names = source.bindGroupLayouts.flatMap { it.uniforms() }.map { it.name() }.toSet()
            return Binding(source, checkNotNull(vertices).buffer(), if (indexed) indices else null,
                if (indexed) indexType else null, clip, textures.filterKeys { it in names }, uniforms.filterKeys { it in names })
        }
        override fun drawIndexed(indexCount: Int, instanceCount: Int, firstIndex: Int, vertexOffset: Int, firstInstance: Int) {
            check(instanceCount == 1 && firstInstance == 0)
            if (clip?.visible() == false) return
            val stride = checkNotNull(source.getVertexFormatBinding(0)).vertexSize
            val offset = Math.toIntExact(checkNotNull(vertices).offset() / stride)
            packets.add(Packet(binding(true), Range(indexCount, firstIndex, vertexOffset + offset, property)))
        }
        override fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {
            check(instanceCount == 1 && firstInstance == 0)
            if (clip?.visible() == false) return
            val stride = checkNotNull(source.getVertexFormatBinding(0)).vertexSize
            packets.add(Packet(binding(false), Range(vertexCount, firstVertex + Math.toIntExact(checkNotNull(vertices).offset() / stride), 0, property)))
        }
        override fun close() = Unit
        override fun pushDebugGroup(label: Supplier<String>) = Unit
        override fun popDebugGroup() = Unit
        override fun writeTimestamp(pool: GpuQueryPool, index: Int): Unit = error("录制 pass 不支持时间查询")
        override fun pushConstants(value: ByteBuffer): Unit = error("录制 pass 不支持 push constants")
        override fun multiDrawIndexed(drawParameters: IntBuffer, instanceCount: Int, firstInstance: Int, drawCount: Int): Unit = error("录制 pass 只接受基础 draw")
        override fun multiDrawIndexed(firstIndexOffsets: PointerBuffer, indexCounts: IntBuffer, vertexOffsets: IntBuffer, drawCount: Int): Unit = error("录制 pass 只接受基础 draw")
        override fun drawIndexedIndirect(commands: GpuBufferSlice, drawCount: Int): Unit = error("录制 pass 只接受基础 draw")
        override fun <T : Any> drawMultipleIndexed(draws: Collection<RenderPass.Draw<T>>, defaultIndexBuffer: GpuBuffer?, defaultIndexType: IndexType?, dynamicUniforms: Collection<String>, uniformArgument: T): Unit = error("录制 pass 只接受基础 draw")
        override fun multiDraw(drawParameters: IntBuffer, instanceCount: Int, firstInstance: Int, drawCount: Int): Unit = error("录制 pass 只接受基础 draw")
        override fun multiDraw(firstVertices: IntBuffer, vertexCounts: IntBuffer, drawCount: Int): Unit = error("录制 pass 只接受基础 draw")
        override fun drawIndirect(commands: GpuBufferSlice, drawCount: Int): Unit = error("录制 pass 只接受基础 draw")
    }
}
