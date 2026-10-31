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
import java.util.*

class RoundRectRenderer private constructor() : IRenderer {

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
        private const val STRIDE = 48
        private const val RECT_BYTES = STRIDE * 4L

        fun create(): RoundRectRenderer = RendererManager.register(RoundRectRenderer())

    }

    fun addRoundRect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Color) {
        addRoundRect(x, y, width, height, radius, radius, radius, radius, color)
    }

    fun addRoundRect(
        x: Float, y: Float, width: Float, height: Float,
        rTL: Float, rTR: Float, rBR: Float, rBL: Float, color: Color,
    ) {
        addRoundRectGradient(x, y, width, height, rTL, rTR, rBR, rBL, color, color, color, color)
    }

    fun addVerticalGradient(x: Float, y: Float, width: Float, height: Float, radius: Float, top: Color, bottom: Color) {
        addRoundRectGradient(x, y, width, height, radius, radius, radius, radius, top, bottom, bottom, top)
    }

    fun addVerticalGradient(
        x: Float, y: Float, width: Float, height: Float,
        rTL: Float, rTR: Float, rBR: Float, rBL: Float, top: Color, bottom: Color,
    ) {
        addRoundRectGradient(x, y, width, height, rTL, rTR, rBR, rBL, top, bottom, bottom, top)
    }

    fun addHorizontalGradient(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        left: Color,
        right: Color
    ) {
        addRoundRectGradient(x, y, width, height, radius, radius, radius, radius, left, left, right, right)
    }

    fun addHorizontalGradient(
        x: Float, y: Float, width: Float, height: Float,
        rTL: Float, rTR: Float, rBR: Float, rBL: Float, left: Color, right: Color,
    ) {
        addRoundRectGradient(x, y, width, height, rTL, rTR, rBR, rBL, left, left, right, right)
    }

    /**
     * 颜色顺序对应四个角顶点：左上、左下、右下、右上 (TL, BL, BR, TR)
     */
    fun addRoundRectGradient(
        x: Float, y: Float, width: Float, height: Float,
        rTL: Float, rTR: Float, rBR: Float, rBL: Float,
        cTL: Color, cBL: Color, cBR: Color, cTR: Color,
    ) {
        buffer.ensureCapacity(currentOffset + RECT_BYTES)
        buffer.tryMap()
        val x2 = x + width
        val y2 = y + height
        val argbTL = ARGB.toABGR(cTL.rgb)
        val argbBL = ARGB.toABGR(cBL.rgb)
        val argbBR = ARGB.toABGR(cBR.rgb)
        val argbTR = ARGB.toABGR(cTR.rgb)

        addVertex(x, y, x, y, x2, y2, rTL, rTR, rBR, rBL, argbTL)
        addVertex(x, y2, x, y, x2, y2, rTL, rTR, rBR, rBL, argbBL)
        addVertex(x2, y2, x, y, x2, y2, rTL, rTR, rBR, rBL, argbBR)
        addVertex(x2, y, x, y, x2, y2, rTL, rTR, rBR, rBL, argbTR)
    }

    private fun addVertex(
        vx: Float, vy: Float, rx1: Float, ry1: Float, rx2: Float, ry2: Float,
        r1: Float, r2: Float, r3: Float, r4: Float, color: Int,
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
        currentOffset += STRIDE
        vertexCount++
    }

    override fun draw() {
        if (vertexCount == 0) return
        if (buffer.isMapped()) buffer.unmap()

        val info = LuminRenderSystem.prepareQuadRendering(vertexCount) ?: return
        val colorView = info.colorView
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return

        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { "Round Rect Draw" }, colorView, Optional.empty<Vector4fc>(),
            info.depthView, OptionalDouble.empty()
        ).use { pass ->
            pass.setPipeline(RenderSystem.getCompiledPipeline(LuminRenderPipelines.ROUND_RECT))
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

}
