package com.github.epsilon.graphics.schedulers.render2d

import com.github.epsilon.graphics.LuminRenderPipelines
import com.github.epsilon.graphics.schedulers.render2d.Render2DDrawPlan.Binding
import com.github.epsilon.graphics.schedulers.render2d.Render2DDrawPlan.Packet
import com.github.epsilon.graphics.schedulers.render2d.Render2DDrawPlan.Range
import com.github.epsilon.graphics.schedulers.render2d.Render2DDrawPlan.Texture
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.pipeline.IndexType
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
import kotlin.test.*

class Render2DDrawPlanTest {
    private class Buffer : GpuBuffer {
        override fun size() = 65536L
        override fun usage() = GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_INDEX
        override fun isClosed() = false
        override fun close() = Unit
        override fun map(offset: Long, length: Long, read: Boolean, write: Boolean): GpuBufferSlice.MappedView = error("无需 GPU")
    }
    private class View : GpuTextureView {
        override fun texture(): GpuTexture = error("无需 GPU")
        override fun baseMipLevel() = 0
        override fun mipLevels() = 1
        override fun getWidth(mipLevel: Int) = 1024
        override fun getHeight(mipLevel: Int) = 1024
        override fun close() = Unit
        override fun isClosed() = false
    }
    private val shapePage = Buffer()
    private val textPage = Buffer()
    private val indices = Buffer()
    private fun text(texture: Texture, clip: Render2DScissor? = null) = Binding(
        LuminRenderPipelines.TTF_FONT_AA, textPage, indices, IndexType.SHORT, clip,
        mapOf("Sampler0" to texture), emptyMap())
    private fun packet(binding: Binding, property: Int) = Packet(binding, Range(6, 0, property * 4, property))

    @Test fun alternatingAtlasesKeepOrderAndDuplicateSourcePropertiesPerRange() {
        val a = Texture(View(), null); val b = Texture(View(), null)
        val packets = listOf(packet(text(a), 0), packet(text(b), 0), packet(text(a), 1))
        val plan = Render2DDrawPlan.build(packets)
        assertEquals(1, plan.runs.size)
        assertEquals(listOf(0, 1, 0), plan.properties.map { it.textureSlot })
        assertEquals(listOf(0, 0, 1), plan.properties.map { it.source })
        assertEquals(listOf(0, 1, 2), plan.runs.single().ranges.map { it.property })
        assertEquals(packets.map { it.range.baseVertex }, plan.runs.single().ranges.map { it.baseVertex })
        assertEquals(a, plan.runs.single().binding.textures["Sampler0"])
        assertEquals(b, plan.runs.single().binding.textures["Sampler1"])
    }

    @Test fun fullTableSplitsWithoutReorderingOrLosingARepeatedAtlas() {
        val textures = List(9) { Texture(View(), null) }
        val packets = (textures + textures.first()).mapIndexed { i, texture -> packet(text(texture), i) }
        val plan = Render2DDrawPlan.build(packets)
        assertEquals(listOf(8, 2), plan.runs.map { it.ranges.size })
        assertEquals((0..9).toList(), plan.properties.map { it.source })
        assertEquals(listOf(0, 1), plan.properties.takeLast(2).map { it.textureSlot })
    }

    @Test fun clipPageUniformAndPipelineChangesRemainBoundaries() {
        val texture = Texture(View(), null)
        val base = text(texture)
        val variants = listOf(base.copy(clip = Render2DScissor(0, 0, 10, 10)), base.copy(vertices = Buffer()),
            base.copy(uniforms = mapOf("Extra" to Buffer().slice())),
            base.copy(pipeline = LuminRenderPipelines.TTF_FONT_NO_AA), base.copy(indices = Buffer()),
            base.copy(indices = null, indexType = null), base.copy(indexType = IndexType.INT))
        for (other in variants) {
            val plan = Render2DDrawPlan.build(listOf(packet(base, 0), packet(other, 1), packet(base, 2)))
            assertEquals(3, plan.runs.size)
            assertEquals(listOf(0, 1, 2), plan.properties.map { it.source })
        }
    }

    @Test fun effectTexturesRemainSeparateAndAllTableSlotsAreBound() {
        val a = Texture(View(), null); val b = Texture(View(), null)
        val effect = text(a).copy(pipeline = LuminRenderPipelines.TTF_FONT_BLUR)
        val effects = listOf(effect, effect.copy(textures = mapOf("Sampler0" to b)), effect)
        val plan = Render2DDrawPlan.build(effects.mapIndexed { i, binding -> packet(binding, i) })
        assertEquals(3, plan.runs.size)
        assertTrue(plan.runs.all { it.binding.textures.size == 1 })
        val table = Render2DDrawPlan.build(listOf(packet(text(a), 0))).runs.single().binding.textures
        assertEquals((0..7).map { "Sampler$it" }.toSet(), table.keys)
        assertTrue(table.values.all { it == a })
    }

    @Test fun retainedTextPipelineDeclaresTheWholeTextureTable() {
        for (source in listOf(LuminRenderPipelines.TTF_FONT_AA, LuminRenderPipelines.TTF_FONT_NO_AA)) {
            val variant = Render2DPipelines.variant(source)
            val names = variant.bindGroupLayouts.flatMap { it.uniforms() }.map { it.name() }
            assertTrue((0..7).all { "Sampler$it" in names })
            assertEquals(1, names.count { it == "Sampler0" })
            assertTrue("EPSILON_TEXTURE_TABLE" in variant.shaderDefines.flags())
            assertFalse("EPSILON_TEXTURE_TABLE" in source.shaderDefines.flags())
        }
    }

    @Test fun categoryPanelWithFortyRowsUsesSixRunsIncludingShadowHeaderAndScrollbar() {
        val defaultAtlas = Texture(View(), null); val iconAtlas = Texture(View(), null)
        val clip = Render2DScissor(0, 28, 130, 300)
        val shape = Binding(LuminRenderPipelines.UI_SHAPE, shapePage, indices, IndexType.SHORT, null, emptyMap(), emptyMap())
        val packets = ArrayList<Packet>()
        fun add(binding: Binding) { packets.add(packet(binding, packets.size)) }
        add(shape.copy(pipeline = LuminRenderPipelines.SHADOW, vertices = Buffer()))
        add(shape); add(shape) // 面板底色和箭头共用形状管线。
        add(text(iconAtlas)); add(text(defaultAtlas))
        repeat(40) { repeat(3) { add(shape.copy(clip = clip)) } }
        repeat(40) {
            add(text(defaultAtlas, clip)); add(text(defaultAtlas, clip)); add(text(iconAtlas, clip))
        }
        add(shape.copy(clip = clip)) // 最后绘制滚动条，保留遮挡关系。
        val plan = Render2DDrawPlan.build(packets)
        assertEquals(6, plan.runs.size)
        assertEquals(120, plan.runs[3].ranges.size)
        assertEquals(120, plan.runs[4].ranges.size)
        assertEquals(packets.size, plan.runs.sumOf { it.ranges.size })
        assertEquals(packets.indices.toList(), plan.properties.map { it.source })
        assertEquals(5, Render2DDrawPlan.build(packets.dropLast(1)).runs.size)
    }

    @Test fun mainPanelIconGridBatchesSevenRetainedButtonsIntoTwoRuns() {
        val clip = Render2DScissor(0, 28, 160, 200)
        val shape = Binding(LuminRenderPipelines.UI_SHAPE, shapePage, indices, IndexType.SHORT, clip, emptyMap(), emptyMap())
        val icons = text(Texture(View(), null), clip)
        val interleaved = (0 until 7).flatMap { index ->
            listOf(packet(shape, index * 2), packet(icons, index * 2 + 1))
        }
        assertEquals(14, Render2DDrawPlan.build(interleaved).runs.size)
        // 两个局部阶段只交换不同按钮之间的底板和图标；每个图标仍在自身底板之后。
        val staged = interleaved.filter { it.binding == shape } + interleaved.filter { it.binding == icons }
        val plan = Render2DDrawPlan.build(staged)
        assertEquals(listOf(7, 7), plan.runs.map { it.ranges.size })
        assertEquals(staged.map { it.range.property }, plan.properties.map { it.source })
        assertTrue(plan.runs.all { it.binding.clip == clip })
        for (index in 0 until 7) {
            assertTrue(plan.properties.indexOfFirst { it.source == index * 2 } <
                plan.properties.indexOfFirst { it.source == index * 2 + 1 })
        }
        // 后续设置或滚动条的形状仍在图标之后，不能被拉入底板批次。
        val settings = packet(shape, 14)
        val withSettings = Render2DDrawPlan.build(staged + settings)
        assertEquals(listOf(7, 7, 1), withSettings.runs.map { it.ranges.size })
        assertEquals(14, withSettings.properties.last().source)
    }
}
