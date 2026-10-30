package com.github.epsilon.graphics.renderers

import com.github.epsilon.graphics.LuminRenderPipelines
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.buffer.LuminRingBuffer
import com.github.epsilon.managers.RendererManager
import com.github.epsilon.utils.render.ScissorUtils
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.backend.opengl.GlStateManager
import net.minecraft.client.renderer.DynamicGpuDataStorage
import net.minecraft.util.ARGB
import org.joml.Vector4fc
import org.lwjgl.system.MemoryUtil
import java.awt.Color
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.Optional
import java.util.OptionalDouble

// 段数上限同时被 SegmentedShadowUniforms 的 UBO 布局使用，放在文件级以便嵌套类型直接引用。
private const val MAX_SEGMENTS = 64

class ShadowRenderer private constructor() : IRenderer {

    private val buffer = LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX)
    private val segmentedBuffer = LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX)
    private val segmentedShadows = ArrayList<SegmentedShadow>()
    private val preparedSegmentedShadows = ArrayList<PreparedSegmentedShadow>()
    private val draws = ArrayList<ShadowDraw>()

    private var scissorEnabled = false
    private var scissorX = 0
    private var scissorY = 0
    private var scissorW = 0
    private var scissorH = 0
    private var currentOffset = 0L
    private var segmentedOffset = 0L
    private var vertexCount = 0
    private var segmentedVertexCount = 0
    private var sharedInfo: LuminRenderSystem.QuadRenderingInfo? = null

    companion object {

        private const val BUFFER_SIZE = 16 * 1024L
        private const val STRIDE = 48
        private const val SHADOW_BYTES = STRIDE * 4L
        private const val SEGMENTED_STRIDE = 12
        private const val SEGMENTED_SHADOW_BYTES = SEGMENTED_STRIDE * 4L
        private val SEGMENTED_UNIFORMS_SIZE = segmentedUniformsSize()

        @JvmStatic
        fun create(): ShadowRenderer = RendererManager.INSTANCE.register(ShadowRenderer())

        private fun clampSegmentCount(segmentRects: FloatArray?, segmentCount: Int): Int {
            if (segmentRects == null || segmentCount <= 0) {
                return 0
            }
            return Math.min(MAX_SEGMENTS, Math.min(segmentCount, segmentRects.size / 4))
        }

        private fun segmentedUniformsSize(): Int {
            val calculator = Std140SizeCalculator().putVec4().putVec4()
            for (i in 0 until MAX_SEGMENTS * 3) {
                calculator.putVec4()
            }
            return calculator.get()
        }

    }

    fun addShadow(x: Float, y: Float, width: Float, height: Float, radius: Float, blurRadius: Float, color: Color) {
        addShadow(x, y, width, height, radius, radius, radius, radius, blurRadius, color)
    }

    fun addShadow(
        x: Float, y: Float, width: Float, height: Float,
        rTL: Float, rTR: Float, rBR: Float, rBL: Float, blurRadius: Float, color: Color,
    ) {
        val firstIndex = vertexCount / 4 * 6
        val x2 = x + width
        val y2 = y + height
        val left = x - blurRadius
        val top = y - blurRadius
        val right = x2 + blurRadius
        val bottom = y2 + blurRadius

        buffer.ensureCapacity(currentOffset + SHADOW_BYTES)
        buffer.tryMap()

        val abgr = ARGB.toABGR(color.getRGB())

        addVertex(left, top, x, y, x2, y2, rTL, rTR, rBR, rBL, blurRadius, abgr)
        addVertex(left, bottom, x, y, x2, y2, rTL, rTR, rBR, rBL, blurRadius, abgr)
        addVertex(right, bottom, x, y, x2, y2, rTL, rTR, rBR, rBL, blurRadius, abgr)
        addVertex(right, top, x, y, x2, y2, rTL, rTR, rBR, rBL, blurRadius, abgr)
        appendNormalDraw(firstIndex)
    }

    fun addShadow(
        x: Float, y: Float, width: Float, height: Float, radius: Float, blurRadius: Float, color: Color,
        segmentRects: FloatArray?, segmentRadii: FloatArray?, segmentCount: Int,
    ) {
        addShadow(
            x, y, width, height, radius, radius, radius, radius, blurRadius, color,
            segmentRects, segmentRadii, null, segmentCount
        )
    }

    fun addShadow(
        x: Float, y: Float, width: Float, height: Float, radius: Float, blurRadius: Float, color: Color,
        segmentRects: FloatArray?, segmentRadii: FloatArray?, segmentColors: FloatArray?, segmentCount: Int,
    ) {
        addShadow(
            x, y, width, height, radius, radius, radius, radius, blurRadius, color,
            segmentRects, segmentRadii, segmentColors, segmentCount
        )
    }

    fun addShadow(
        x: Float, y: Float, width: Float, height: Float,
        rTL: Float, rTR: Float, rBR: Float, rBL: Float, blurRadius: Float, color: Color,
        segmentRects: FloatArray?, segmentRadii: FloatArray?, segmentColors: FloatArray?, segmentCount: Int,
    ) {
        val count = clampSegmentCount(segmentRects, segmentCount)
        if (count == 0) {
            addShadow(x, y, width, height, rTL, rTR, rBR, rBL, blurRadius, color)
            return
        }

        // count > 0 时 clampSegmentCount 已保证 segmentRects 非空。
        val rectsInput = segmentRects!!

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (i in 0 until count) {
            val offset = i * 4
            val segmentX = rectsInput[offset]
            val segmentY = rectsInput[offset + 1]
            val segmentWidth = Math.max(0.0f, rectsInput[offset + 2])
            val segmentHeight = Math.max(0.0f, rectsInput[offset + 3])
            minX = Math.min(minX, segmentX)
            minY = Math.min(minY, segmentY)
            maxX = Math.max(maxX, segmentX + segmentWidth)
            maxY = Math.max(maxY, segmentY + segmentHeight)
        }

        if (!minX.isFinite() || !minY.isFinite() || maxX <= minX || maxY <= minY) {
            addShadow(x, y, width, height, rTL, rTR, rBR, rBL, blurRadius, color)
            return
        }

        val range = Math.max(0.0f, blurRadius)
        val left = minX - range
        val top = minY - range
        val right = maxX + range
        val bottom = maxY + range

        segmentedBuffer.ensureCapacity(segmentedOffset + SEGMENTED_SHADOW_BYTES)
        segmentedBuffer.tryMap()
        addSegmentedVertex(left, top)
        addSegmentedVertex(left, bottom)
        addSegmentedVertex(right, bottom)
        addSegmentedVertex(right, top)

        val rects = rectsInput.copyOf(count * 4)
        val radii = if (segmentRadii == null) FloatArray(count) else segmentRadii.copyOf(count)
        val shadowIndex = segmentedShadows.size
        val colors = if (segmentColors == null) null else segmentColors.copyOf(count * 3)
        segmentedShadows.add(SegmentedShadow(range, color, rects, radii, colors, count))
        draws.add(ShadowDraw(true, shadowIndex * 6, 6, shadowIndex))
    }

    private fun addVertex(
        x: Float, y: Float, innerX1: Float, innerY1: Float, innerX2: Float, innerY2: Float,
        rTL: Float, rTR: Float, rBR: Float, rBL: Float, blurRadius: Float, color: Int,
    ) {
        val address = MemoryUtil.memAddress(buffer.getMappedBuffer()) + currentOffset

        MemoryUtil.memPutFloat(address, x)
        MemoryUtil.memPutFloat(address + 4, y)
        MemoryUtil.memPutFloat(address + 8, blurRadius)
        MemoryUtil.memPutInt(address + 12, color)

        MemoryUtil.memPutFloat(address + 16, innerX1)
        MemoryUtil.memPutFloat(address + 20, innerY1)
        MemoryUtil.memPutFloat(address + 24, innerX2)
        MemoryUtil.memPutFloat(address + 28, innerY2)

        MemoryUtil.memPutFloat(address + 32, rTL)
        MemoryUtil.memPutFloat(address + 36, rTR)
        MemoryUtil.memPutFloat(address + 40, rBR)
        MemoryUtil.memPutFloat(address + 44, rBL)

        currentOffset += STRIDE
        vertexCount++
    }

    private fun addSegmentedVertex(x: Float, y: Float) {
        val address = MemoryUtil.memAddress(segmentedBuffer.getMappedBuffer()) + segmentedOffset
        MemoryUtil.memPutFloat(address, x)
        MemoryUtil.memPutFloat(address + 4, y)
        MemoryUtil.memPutFloat(address + 8, 0.0f)
        segmentedOffset += SEGMENTED_STRIDE
        segmentedVertexCount++
    }

    fun setScissor(x: Int, y: Int, width: Int, height: Int) {
        val scissor = ScissorUtils.clampFramebufferScissor(x, y, width, height)
        scissorEnabled = true
        scissorX = scissor.x
        scissorY = scissor.y
        scissorW = scissor.width
        scissorH = scissor.height
    }

    fun clearScissor() {
        scissorEnabled = false
    }

    override fun draw() {
        if (!prepare(true)) return

        try {
            val info = sharedInfo ?: return
            RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                { "Lumin Shadow Draw" }, info.colorView, Optional.empty<Vector4fc>(),
                info.depthView, OptionalDouble.empty()
            ).use { pass ->
                RenderSystem.bindDefaultUniforms(pass)
                drawPrepared(pass, info)
            }
        } finally {
            GlStateManager._disableScissorTest()
        }
    }

    override fun prepareSharedDraw(): Boolean {
        return prepare(false)
    }

    private fun prepare(applyProjection: Boolean): Boolean {
        sharedInfo = null
        preparedSegmentedShadows.clear()
        if (vertexCount == 0 && segmentedVertexCount == 0) return false
        if (buffer.isMapped()) buffer.unmap()
        if (segmentedBuffer.isMapped()) segmentedBuffer.unmap()
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return false

        val info = LuminRenderSystem.prepareQuadRendering(Math.max(vertexCount, segmentedVertexCount), applyProjection)
        sharedInfo = info
        if (info == null || info.colorView == null) return false

        for (i in 0 until segmentedShadows.size) {
            val shadow = segmentedShadows[i]
            val uniforms = LuminRenderSystem.writeDynamicUniform(
                "segmented_shadow_uniforms",
                "Lumin Segmented Shadow UBO",
                SEGMENTED_UNIFORMS_SIZE,
                16,
                SegmentedShadowUniforms(shadow)
            )
            preparedSegmentedShadows.add(PreparedSegmentedShadow(uniforms))
        }
        return true
    }

    override fun draw(pass: RenderPass) {
        val info = sharedInfo ?: return
        try {
            pass.setUniform("DynamicTransforms", info.dynamicUniforms)
            drawPrepared(pass, info)
        } finally {
            GlStateManager._disableScissorTest()
        }
    }

    private fun drawPrepared(pass: RenderPass, info: LuminRenderSystem.QuadRenderingInfo) {
        if (scissorEnabled) {
            if (!ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)) {
                return
            }
        } else {
            pass.disableScissor()
        }

        pass.setIndexBuffer(LuminRenderSystem.getQuadIndexBuffer(info.indexCount), LuminRenderSystem.getQuadIndexType())
        pass.setUniform("DynamicTransforms", info.dynamicUniforms)

        for (drawCall in draws) {
            if (drawCall.segmented) {
                val shadow = preparedSegmentedShadows[drawCall.segmentedShadowIndex]
                pass.setPipeline(RenderSystem.getCompiledPipeline(LuminRenderPipelines.SEGMENTED_SHADOW))
                pass.setVertexBuffer(0, segmentedBuffer.getGpuBuffer().slice())
                pass.setUniform("SegmentedShadowUniforms", shadow.uniforms)
                pass.drawIndexed(drawCall.indexCount, 1, drawCall.firstIndex, 0, 0)
            } else {
                pass.setPipeline(RenderSystem.getCompiledPipeline(LuminRenderPipelines.SHADOW))
                pass.setVertexBuffer(0, buffer.getGpuBuffer().slice())
                pass.drawIndexed(drawCall.indexCount, 1, drawCall.firstIndex, 0, 0)
            }
        }
    }

    override fun clear() {
        if (vertexCount > 0) {
            if (buffer.isMapped()) buffer.unmap()
            buffer.rotate()
        }
        if (segmentedVertexCount > 0) {
            if (segmentedBuffer.isMapped()) segmentedBuffer.unmap()
            segmentedBuffer.rotate()
        }

        currentOffset = 0L
        segmentedOffset = 0L
        vertexCount = 0
        segmentedVertexCount = 0
        segmentedShadows.clear()
        preparedSegmentedShadows.clear()
        draws.clear()
        sharedInfo = null
    }

    override fun close() {
        clear()
        buffer.close()
        segmentedBuffer.close()
        RendererManager.INSTANCE.unregister(this)
    }

    private fun appendNormalDraw(firstIndex: Int) {
        if (draws.isNotEmpty()) {
            val previous = draws.last()
            if (!previous.segmented && previous.firstIndex + previous.indexCount == firstIndex) {
                draws[draws.size - 1] = ShadowDraw(false, previous.firstIndex, previous.indexCount + 6, -1)
                return
            }
        }
        draws.add(ShadowDraw(false, firstIndex, 6, -1))
    }

    @JvmRecord
    private data class SegmentedShadow(
        val blurRadius: Float,
        val color: Color,
        val rects: FloatArray,
        val radii: FloatArray,
        val colors: FloatArray?,
        val count: Int,
    )

    @JvmRecord
    private data class PreparedSegmentedShadow(val uniforms: GpuBufferSlice)

    @JvmRecord
    private data class ShadowDraw(
        val segmented: Boolean,
        val firstIndex: Int,
        val indexCount: Int,
        val segmentedShadowIndex: Int,
    )

    @JvmRecord
    private data class SegmentedShadowUniforms(val shadow: SegmentedShadow) : DynamicGpuDataStorage.DynamicGpuData {

        override fun write(buffer: ByteBuffer) {
            val color = shadow.color
            val builder = Std140Builder.intoBuffer(buffer)
                .putVec4(
                    color.red / 255.0f,
                    color.green / 255.0f,
                    color.blue / 255.0f,
                    color.alpha / 255.0f
                )
                .putVec4(shadow.blurRadius, shadow.count.toFloat(), 0.0f, 0.0f)

            for (i in 0 until MAX_SEGMENTS) {
                if (i < shadow.count) {
                    val offset = i * 4
                    builder.putVec4(
                        shadow.rects[offset],
                        shadow.rects[offset + 1],
                        Math.max(0.0f, shadow.rects[offset + 2]),
                        Math.max(0.0f, shadow.rects[offset + 3])
                    )
                } else {
                    builder.putVec4(0.0f, 0.0f, 0.0f, 0.0f)
                }
            }

            for (i in 0 until MAX_SEGMENTS) {
                val radius = if (i < shadow.count) Math.max(0.0f, shadow.radii[i]) else 0.0f
                builder.putVec4(radius, 0.0f, 0.0f, 0.0f)
            }

            for (i in 0 until MAX_SEGMENTS) {
                val colors = shadow.colors
                if (colors != null && i < shadow.count && colors.size >= i * 3 + 3) {
                    val offset = i * 3
                    builder.putVec4(colors[offset], colors[offset + 1], colors[offset + 2], 1.0f)
                } else {
                    builder.putVec4(color.red / 255.0f, color.green / 255.0f, color.blue / 255.0f, 1.0f)
                }
            }
        }

    }

}
