package com.github.epsilon.graphics.schedulers.render2d

import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.pipeline.IndexType
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTextureView

/** 保留 packet 顺序；普通文字允许每个范围选择绑定表中的图集，不因字体切换拆批。 */
internal object Render2DDrawPlan {
    const val TEXTURE_SLOTS = 8

    data class Texture(val view: GpuTextureView?, val sampler: GpuSampler?)
    data class Binding(
        val pipeline: RenderPipeline, val vertices: GpuBuffer, val indices: GpuBuffer?,
        val indexType: IndexType?, val clip: Render2DScissor?,
        val textures: Map<String, Texture>, val uniforms: Map<String, GpuBufferSlice>
    )

    data class Range(val count: Int, val first: Int, val baseVertex: Int, val property: Int)
    data class Packet(val binding: Binding, val range: Range)
    data class Run(val binding: Binding, val ranges: List<Range>)
    data class Property(val source: Int, val textureSlot: Int)
    data class Plan(val runs: List<Run>, val properties: List<Property>)

    fun build(packets: List<Packet>): Plan {
        val runs = ArrayList<Run>()
        val properties = ArrayList<Property>()
        var binding: Binding? = null
        var ranges = ArrayList<Range>()
        val textures = ArrayList<Texture>()
        fun flush() {
            val current = binding ?: return
            val resources = if (textures.isEmpty()) current.textures else
                (0 until TEXTURE_SLOTS).associate { "Sampler$it" to textures.getOrElse(it) { textures.first() } }
            runs.add(Run(current.copy(textures = resources), ranges))
            ranges = ArrayList()
            textures.clear()
        }
        for ((original, range) in packets) {
            val table = Render2DPipelines.usesTextureTable(original.pipeline)
            val texture = if (table) original.textures.getValue("Sampler0") else null
            val key = if (table) original.copy(textures = emptyMap()) else original
            if (binding != key || texture != null && texture !in textures && textures.size == TEXTURE_SLOTS) {
                flush()
                binding = key
            }
            val slot = if (texture == null) 0 else textures.indexOf(texture).takeIf { it >= 0 }
                ?: textures.size.also { textures.add(texture) }
            ranges.add(range.copy(property = properties.size))
            properties.add(Property(range.property, slot))
        }
        flush()
        return Plan(runs, properties)
    }
}
