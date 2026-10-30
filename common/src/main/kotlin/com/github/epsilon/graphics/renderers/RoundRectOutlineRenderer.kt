package com.github.epsilon.graphics.renderers

import com.github.epsilon.graphics.LuminRenderPipelines
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.buffer.LuminRingBuffer
import com.github.epsilon.managers.RendererManager
import com.github.epsilon.utils.render.ScissorUtils
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.commands.RenderPass
import net.minecraft.util.ARGB
import org.joml.Vector4fc
import org.lwjgl.system.MemoryUtil
import java.awt.Color
import java.util.Optional
import java.util.OptionalDouble

class RoundRectOutlineRenderer private constructor() : IRenderer {

    private val buffer = LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX)

    private var scissorEnabled = false
    private var scissorX = 0
    private var scissorY = 0
    private var scissorW = 0
    private var scissorH = 0
    private var currentOffset = 0L
    private var vertexCount = 0
    private var sharedInfo: LuminRenderSystem.QuadRenderingInfo? = null

    companion object {

        private const val BUFFER_SIZE = 16 * 1024L
        private const val STRIDE = 52
        private const val OUTLINE_BYTES = STRIDE * 4L

        fun create(): RoundRectOutlineRenderer = RendererManager.register(RoundRectOutlineRenderer())

    }

    fun addOutline(
        x: Float, y: Float, width: Float, height: Float,
        radius: Float, outlineWidth: Float, color: Color,
    ) {
        addOutline(x, y, width, height, radius, radius, radius, radius, outlineWidth, color)
    }

    fun addOutline(
        x: Float, y: Float, width: Float, height: Float,
        radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
        outlineWidth: Float, color: Color,
    ) {
        addOutlineGradient(
            x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
            outlineWidth, color, color, color, color
        )
    }

    fun addVerticalGradient(
        x: Float, y: Float, width: Float, height: Float,
        radius: Float, outlineWidth: Float, top: Color, bottom: Color,
    ) {
        addVerticalGradient(x, y, width, height, radius, radius, radius, radius, outlineWidth, top, bottom)
    }

    fun addVerticalGradient(
        x: Float, y: Float, width: Float, height: Float,
        radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
        outlineWidth: Float, top: Color, bottom: Color,
    ) {
        addOutlineGradient(
            x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
            outlineWidth, top, bottom, bottom, top
        )
    }

    fun addHorizontalGradient(
        x: Float, y: Float, width: Float, height: Float,
        radius: Float, outlineWidth: Float, left: Color, right: Color,
    ) {
        addHorizontalGradient(x, y, width, height, radius, radius, radius, radius, outlineWidth, left, right)
    }

    fun addHorizontalGradient(
        x: Float, y: Float, width: Float, height: Float,
        radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
        outlineWidth: Float, left: Color, right: Color,
    ) {
        addOutlineGradient(
            x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
            outlineWidth, left, left, right, right
        )
    }

    /**
     * 颜色顺序对应四个角顶点：左上、左下、右下、右上 (TL, BL, BR, TR)
     */
    fun addOutlineGradient(
        x: Float, y: Float, width: Float, height: Float,
        radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
        outlineWidth: Float, colorTopLeft: Color, colorBottomLeft: Color, colorBottomRight: Color, colorTopRight: Color,
    ) {
        if (outlineWidth <= 0.0f) return

        buffer.ensureCapacity(currentOffset + OUTLINE_BYTES)
        buffer.tryMap()

        val halfOutline = outlineWidth * 0.5f
        val x2 = x + width
        val y2 = y + height
        val outerX1 = x - halfOutline
        val outerY1 = y - halfOutline
        val outerX2 = x2 + halfOutline
        val outerY2 = y2 + halfOutline
        val argbTopLeft = ARGB.toABGR(colorTopLeft.getRGB())
        val argbBottomLeft = ARGB.toABGR(colorBottomLeft.getRGB())
        val argbBottomRight = ARGB.toABGR(colorBottomRight.getRGB())
        val argbTopRight = ARGB.toABGR(colorTopRight.getRGB())

        addVertex(
            outerX1, outerY1, x, y, x2, y2, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
            outlineWidth, argbTopLeft
        )
        addVertex(
            outerX1, outerY2, x, y, x2, y2, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
            outlineWidth, argbBottomLeft
        )
        addVertex(
            outerX2, outerY2, x, y, x2, y2, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
            outlineWidth, argbBottomRight
        )
        addVertex(
            outerX2, outerY1, x, y, x2, y2, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
            outlineWidth, argbTopRight
        )
    }

    private fun addVertex(
        vx: Float, vy: Float, rx1: Float, ry1: Float, rx2: Float, ry2: Float,
        r1: Float, r2: Float, r3: Float, r4: Float, outlineWidth: Float, color: Int,
    ) {
        val baseAddr = MemoryUtil.memAddress(buffer.getMappedBuffer())
        val p = baseAddr + currentOffset

        MemoryUtil.memPutFloat(p, vx)
        MemoryUtil.memPutFloat(p + 4, vy)
        MemoryUtil.memPutFloat(p + 8, 0.0f)
        MemoryUtil.memPutInt(p + 12, color)
        MemoryUtil.memPutFloat(p + 16, rx1)
        MemoryUtil.memPutFloat(p + 20, ry1)
        MemoryUtil.memPutFloat(p + 24, rx2)
        MemoryUtil.memPutFloat(p + 28, ry2)
        MemoryUtil.memPutFloat(p + 32, r1)
        MemoryUtil.memPutFloat(p + 36, r2)
        MemoryUtil.memPutFloat(p + 40, r3)
        MemoryUtil.memPutFloat(p + 44, r4)
        MemoryUtil.memPutFloat(p + 48, outlineWidth)
        currentOffset += STRIDE
        vertexCount++
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
        if (vertexCount == 0) return
        if (buffer.isMapped()) buffer.unmap()

        val info = LuminRenderSystem.prepareQuadRendering(vertexCount) ?: return
        val colorView = info.colorView
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return

        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { "Round Rect Outline Draw" }, colorView, Optional.empty<Vector4fc>(),
            info.depthView, OptionalDouble.empty()
        ).use { pass ->
            pass.setPipeline(RenderSystem.getCompiledPipeline(LuminRenderPipelines.ROUND_RECT_OUTLINE))
            if (scissorEnabled) ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)
            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform("DynamicTransforms", info.dynamicUniforms)
            drawPrepared(pass, info)
        }
    }

    override fun prepareSharedDraw(): Boolean {
        sharedInfo = null
        if (vertexCount == 0) return false
        if (buffer.isMapped()) buffer.unmap()
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return false

        val info = LuminRenderSystem.prepareQuadRendering(vertexCount, false)
        sharedInfo = info
        return info != null
    }

    override fun draw(pass: RenderPass) {
        val info = sharedInfo ?: return
        pass.setUniform("DynamicTransforms", info.dynamicUniforms)
        drawPrepared(pass, info)
    }

    private fun drawPrepared(pass: RenderPass, info: LuminRenderSystem.QuadRenderingInfo) {
        if (scissorEnabled) {
            if (!ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)) {
                return
            }
        } else {
            pass.disableScissor()
        }

        pass.setVertexBuffer(0, buffer.getGpuBuffer().slice())
        pass.setIndexBuffer(LuminRenderSystem.getQuadIndexBuffer(info.indexCount), LuminRenderSystem.getQuadIndexType())
        pass.drawIndexed(info.indexCount, 1, 0, 0, 0)
    }

    override fun clear() {
        if (vertexCount > 0) {
            if (buffer.isMapped()) buffer.unmap()
            buffer.rotate()
        }
        vertexCount = 0
        currentOffset = 0L
        sharedInfo = null
    }

    override fun close() {
        buffer.close()
        RendererManager.unregister(this)
    }

}
