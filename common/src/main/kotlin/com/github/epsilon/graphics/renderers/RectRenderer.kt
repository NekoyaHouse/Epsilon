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

class RectRenderer private constructor() : IRenderer {

    private val buffer = LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX)

    private var currentOffset = 0L
    private var vertexCount = 0

    private var scissorEnabled = false
    private var scissorX = 0
    private var scissorY = 0
    private var scissorW = 0
    private var scissorH = 0
    private var sharedInfo: LuminRenderSystem.QuadRenderingInfo? = null

    companion object {

        private const val BUFFER_SIZE = 16 * 1024L
        private const val STRIDE = 16
        private const val RECT_BYTES = STRIDE * 4L

        @JvmStatic
        fun create(): RectRenderer = RendererManager.INSTANCE.register(RectRenderer())

    }

    fun addRect(x: Float, y: Float, width: Float, height: Float, color: Color) {
        addRectGradient(x, y, width, height, color, color, color, color)
    }

    fun addLine(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Color?) {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        if (!length.isFinite() || length <= 0.001f || color == null) return

        val halfWidth = width / 2.0f
        val normalX = -dy / length * halfWidth
        val normalY = dx / length * halfWidth

        buffer.ensureCapacity(currentOffset + RECT_BYTES)
        buffer.tryMap()

        val argb = ARGB.toABGR(color.getRGB())
        addVertex(x1 + normalX, y1 + normalY, argb)
        addVertex(x1 - normalX, y1 - normalY, argb)
        addVertex(x2 - normalX, y2 - normalY, argb)
        addVertex(x2 + normalX, y2 + normalY, argb)
    }

    fun addOutline(x: Float, y: Float, width: Float, height: Float, outline: Float, color: Color) {
        val sideHeight = height - outline * 2.0f
        addRect(x, y, width, outline, color)
        addRect(x, y + height - outline, width, outline, color)
        addRect(x, y + outline, outline, sideHeight, color)
        addRect(x + width - outline, y + outline, outline, sideHeight, color)
    }

    fun addVerticalGradient(x: Float, y: Float, width: Float, height: Float, top: Color, bottom: Color) {
        addRectGradient(x, y, width, height, top, bottom, bottom, top)
    }

    fun addHorizontalGradient(x: Float, y: Float, width: Float, height: Float, left: Color, right: Color) {
        addRectGradient(x, y, width, height, left, left, right, right)
    }

    fun addRectGradient(x: Float, y: Float, w: Float, h: Float, c1: Color, c2: Color, c3: Color, c4: Color) {
        buffer.ensureCapacity(currentOffset + RECT_BYTES)
        buffer.tryMap()

        val argb1 = ARGB.toABGR(c1.getRGB())
        val argb2 = ARGB.toABGR(c2.getRGB())
        val argb3 = ARGB.toABGR(c3.getRGB())
        val argb4 = ARGB.toABGR(c4.getRGB())

        addVertex(x, y, argb1)
        addVertex(x, y + h, argb2)
        addVertex(x + w, y + h, argb3)
        addVertex(x + w, y, argb4)
    }

    private fun addVertex(vx: Float, vy: Float, color: Int) {
        val baseAddr = MemoryUtil.memAddress(buffer.getMappedBuffer())
        val p = baseAddr + currentOffset

        // Position: float x, y, z (12 bytes)
        MemoryUtil.memPutFloat(p, vx)
        MemoryUtil.memPutFloat(p + 4, vy)
        MemoryUtil.memPutFloat(p + 8, 0.0f)

        // Color: int abgr (4 bytes)
        MemoryUtil.memPutInt(p + 12, color)

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

        if (buffer.isMapped()) {
            buffer.unmap()
        }

        val info = LuminRenderSystem.prepareQuadRendering(vertexCount) ?: return
        val colorView = info.colorView
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return

        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { "Rect Draw" },
            colorView, Optional.empty<Vector4fc>(),
            info.depthView, OptionalDouble.empty()
        ).use { pass ->
            pass.setPipeline(RenderSystem.getCompiledPipeline(LuminRenderPipelines.RECTANGLE))
            if (scissorEnabled) {
                ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)
            }

            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform("DynamicTransforms", info.dynamicUniforms)
            drawPrepared(pass, info)
        }
    }

    override fun prepareSharedDraw(): Boolean {
        sharedInfo = null
        if (vertexCount == 0) return false

        if (buffer.isMapped()) {
            buffer.unmap()
        }

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
            if (buffer.isMapped()) {
                buffer.unmap()
            }
            buffer.rotate()
        }

        vertexCount = 0
        currentOffset = 0L
        sharedInfo = null
    }

    override fun close() {
        clear()
        buffer.close()
        RendererManager.INSTANCE.unregister(this)
    }

}
