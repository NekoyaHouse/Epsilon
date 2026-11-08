package com.github.epsilon.graphics.schedulers.render2d

import com.github.epsilon.graphics.LuminRenderPipelines
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.pipeline.IndexType
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
import kotlin.test.*

class Render2DRecordingPassTest {
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
    private val vertices = Buffer()
    private val indices = Buffer()
    private fun recorder(source: RenderPipeline) = Render2DExecutor.RecordingPass(emptyMap()).also {
        it.source = source
        it.setVertexBuffer(0, vertices.slice())
        it.setIndexBuffer(indices, IndexType.SHORT)
    }

    @Test fun sharedBindingKeepsEachRangeOffsetAndProperty() {
        val pass = recorder(LuminRenderPipelines.UI_SHAPE)
        pass.drawIndexed(6, 1, 0, 0, 0)
        val first = pass.packets.single()
        val stride = checkNotNull(pass.source.getVertexFormatBinding(0)).vertexSize
        pass.setVertexBuffer(0, vertices.slice(stride * 4L, stride * 4L))
        pass.setIndexBuffer(indices, IndexType.SHORT)
        pass.property = 7
        pass.drawIndexed(12, 1, 0, 2, 0)
        assertSame(first.binding, pass.packets.last().binding)
        assertEquals(Render2DDrawPlan.Range(12, 0, 6, 7), pass.packets.last().range)
        assertEquals(Render2DDrawPlan.Range(6, 0, 0, 0), first.range)
        pass.draw(3, 1, 0, 0)
        assertNull(pass.packets.last().binding.indices)
        pass.drawIndexed(6, 1, 0, 0, 0)
        assertSame(indices, pass.packets.last().binding.indices)
        val replacement = Buffer()
        pass.setVertexBuffer(0, replacement.slice())
        pass.drawIndexed(6, 1, 0, 0, 0)
        assertSame(replacement, pass.packets.last().binding.vertices)
        assertSame(vertices, first.binding.vertices)
    }

    @Test fun changingResourcesClipAndPipelineNeverMutatesRecordedPackets() {
        val pass = recorder(LuminRenderPipelines.TTF_FONT_BLUR)
        val a = View(); val b = View()
        val uniforms = Buffer()
        pass.setUniform("Sampler0", a, null)
        pass.setUniform("FontBlurUniforms", uniforms.slice(0, 64))
        pass.enableScissor(1, 2, 30, 40)
        pass.drawIndexed(6, 1, 0, 0, 0)
        val first = pass.packets.single().binding
        pass.setUniform("Sampler0", a, null)
        pass.setUniform("FontBlurUniforms", uniforms.slice(0, 64))
        pass.setUniform("DynamicTransforms", uniforms.slice(64, 64))
        pass.enableScissor(1, 2, 30, 40)
        pass.drawIndexed(6, 1, 0, 0, 0)
        assertSame(first, pass.packets.last().binding)
        pass.setUniform("Sampler0", b, null)
        pass.setUniform("FontBlurUniforms", uniforms.slice(64, 64))
        pass.disableScissor()
        pass.drawIndexed(6, 1, 0, 0, 0)
        val second = pass.packets.last().binding
        assertSame(a, first.textures.getValue("Sampler0").view)
        assertSame(b, second.textures.getValue("Sampler0").view)
        assertEquals(0, first.uniforms.getValue("FontBlurUniforms").offset())
        assertEquals(64, second.uniforms.getValue("FontBlurUniforms").offset())
        assertEquals(Render2DScissor(1, 2, 30, 40), first.clip)
        assertNull(second.clip)
        pass.source = LuminRenderPipelines.UI_SHAPE
        pass.drawIndexed(6, 1, 0, 0, 0)
        assertTrue(pass.packets.last().binding.textures.isEmpty())
        assertTrue(pass.packets.last().binding.uniforms.isEmpty())
        pass.source = LuminRenderPipelines.TTF_FONT_BLUR
        pass.drawIndexed(6, 1, 0, 0, 0)
        assertEquals(second, pass.packets.last().binding)
    }
}
