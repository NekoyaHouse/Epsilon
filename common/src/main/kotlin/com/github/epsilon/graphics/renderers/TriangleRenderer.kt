package com.github.epsilon.graphics.renderers

import com.github.epsilon.graphics.LuminRenderPipelines
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.buffer.LuminRingBuffer
import com.github.epsilon.managers.RendererManager
import com.github.epsilon.utils.render.ScissorUtils
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.renderer.rendertype.TextureTransform
import net.minecraft.util.ARGB
import org.joml.Vector3f
import org.joml.Vector4f
import org.joml.Vector4fc
import org.lwjgl.system.MemoryUtil
import java.awt.Color
import java.util.Optional
import java.util.OptionalDouble

class TriangleRenderer private constructor() : IRenderer {

    private val buffer = LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX)

    private var currentOffset = 0L
    private var vertexCount = 0

    private var scissorEnabled = false
    private var scissorX = 0
    private var scissorY = 0
    private var scissorW = 0
    private var scissorH = 0
    private var sharedDynamicUniforms: GpuBufferSlice? = null

    companion object {

        private const val BUFFER_SIZE = 16 * 1024L
        private const val STRIDE = 16
        private const val TRIANGLE_BYTES = STRIDE * 3L

        fun create(): TriangleRenderer = RendererManager.register(TriangleRenderer())

    }

    fun addChevronTriangle(centerX: Float, centerY: Float, size: Float, progress: Float, color: Color) {
        buffer.ensureCapacity(currentOffset + TRIANGLE_BYTES)
        buffer.tryMap()

        val abgr = ARGB.toABGR(color.getRGB())
        val clampedProgress = Math.clamp(progress, 0.0f, 1.0f)

        val tipXR = centerX + size
        val tipYR = centerY
        val base1XR = centerX - size
        val base1YR = centerY - size
        val base2XR = centerX - size
        val base2YR = centerY + size

        val tipXD = centerX
        val tipYD = centerY + size
        val base1XD = centerX - size
        val base1YD = centerY - size
        val base2XD = centerX + size
        val base2YD = centerY - size

        val x1 = base1XR + (base1XD - base1XR) * clampedProgress
        val y1 = base1YR + (base1YD - base1YR) * clampedProgress
        val x2 = base2XR + (base2XD - base2XR) * clampedProgress
        val y2 = base2YR + (base2YD - base2YR) * clampedProgress
        val x3 = tipXR + (tipXD - tipXR) * clampedProgress
        val y3 = tipYR + (tipYD - tipYR) * clampedProgress

        addVertex(x1, y1, abgr)
        addVertex(x2, y2, abgr)
        addVertex(x3, y3, abgr)
    }

    fun addTriangle(
        x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Color,
    ) {
        buffer.ensureCapacity(currentOffset + TRIANGLE_BYTES)
        buffer.tryMap()

        val abgr = ARGB.toABGR(color.getRGB())
        addVertex(x1, y1, abgr)
        addVertex(x2, y2, abgr)
        addVertex(x3, y3, abgr)
    }

    private fun addVertex(vx: Float, vy: Float, color: Int) {
        val baseAddr = MemoryUtil.memAddress(buffer.getMappedBuffer())
        val p = baseAddr + currentOffset

        MemoryUtil.memPutFloat(p, vx)
        MemoryUtil.memPutFloat(p + 4, vy)
        MemoryUtil.memPutFloat(p + 8, 0.0f)
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

        LuminRenderSystem.applyOrthoProjection()

        val colorView = LuminRenderSystem.resolveColorView()
        val depthView = LuminRenderSystem.resolveDepthView()
        if (colorView == null) return
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return

        val dynamicUniforms = LuminRenderSystem.writeTransform(
            RenderSystem.getModelViewMatrixCopy(),
            Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
            Vector3f(0.0f, 0.0f, 0.0f),
            TextureTransform.DEFAULT_TEXTURING.createMatrix()
        )

        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { "Triangle Draw" },
            colorView, Optional.empty<Vector4fc>(),
            depthView, OptionalDouble.empty()
        ).use { pass ->
            pass.setPipeline(RenderSystem.getCompiledPipeline(LuminRenderPipelines.TRIANGLE))
            if (scissorEnabled) {
                ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)
            }
            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform("DynamicTransforms", dynamicUniforms)
            drawPrepared(pass)
        }
    }

    override fun prepareSharedDraw(): Boolean {
        sharedDynamicUniforms = null
        if (vertexCount == 0) return false

        if (buffer.isMapped()) {
            buffer.unmap()
        }

        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return false

        sharedDynamicUniforms = LuminRenderSystem.writeDefaultGuiTransform()
        return sharedDynamicUniforms != null
    }

    override fun draw(pass: RenderPass) {
        val uniforms = sharedDynamicUniforms ?: return
        pass.setUniform("DynamicTransforms", uniforms)
        drawPrepared(pass)
    }

    private fun drawPrepared(pass: RenderPass) {
        if (scissorEnabled) {
            if (!ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)) {
                return
            }
        } else {
            pass.disableScissor()
        }

        pass.setVertexBuffer(0, buffer.getGpuBuffer().slice())
        pass.draw(vertexCount, 1, 0, 0)
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
        sharedDynamicUniforms = null
    }

    override fun close() {
        clear()
        buffer.close()
        RendererManager.unregister(this)
    }

}
