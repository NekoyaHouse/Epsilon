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

/**
 * 描边圆弧渲染器。
 *
 * 每个圆弧提交一个覆盖其包围盒的四边形，由 SDF 片元着色器算出环体与角度扇区的交集，
 * 因此整条弧共享同一次距离场求值，不会像逐段三角形铺环那样在内部产生接缝。
 */
class ArcRenderer private constructor() : IRenderer {

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
        private const val STRIDE = 56
        private const val ARC_BYTES = STRIDE * 4L
        private val TAU = (Math.PI * 2.0).toFloat()

        fun create(): ArcRenderer = RendererManager.register(ArcRenderer())

    }

    fun addCircle(centerX: Float, centerY: Float, radius: Float, strokeWidth: Float, color: Color) {
        addArc(centerX, centerY, radius, strokeWidth, 0.0f, 360.0f, false, color)
    }

    /**
     * 提交一条描边圆弧。
     *
     * @param centerX      圆心 X
     * @param centerY      圆心 Y
     * @param radius       描边中线半径
     * @param strokeWidth  描边宽度
     * @param startDegrees 起始角度，0 指向右侧，顺时针增大
     * @param sweepDegrees 扫过角度
     * @param roundCap     是否使用圆形端帽
     * @param color        颜色
     */
    fun addArc(
        centerX: Float, centerY: Float, radius: Float, strokeWidth: Float,
        startDegrees: Float, sweepDegrees: Float, roundCap: Boolean, color: Color,
    ) {
        addGradientArc(
            centerX, centerY, radius, strokeWidth, startDegrees, sweepDegrees, roundCap,
            0.0f, color, color, color
        )
    }

    /**
     * 提交一条带三色循环扫描渐变的描边圆弧。
     *
     * @param gradientRotationDegrees 渐变首色所在角度，0 指向右侧，顺时针增大
     * @param startColor              渐变 0% 颜色
     * @param middleColor             渐变 50% 颜色
     * @param endColor                渐变 100% 颜色
     */
    fun addGradientArc(
        centerX: Float, centerY: Float, radius: Float, strokeWidth: Float,
        startDegrees: Float, sweepDegrees: Float, roundCap: Boolean,
        gradientRotationDegrees: Float, startColor: Color, middleColor: Color, endColor: Color,
    ) {
        if (radius <= 0.0f || strokeWidth <= 0.0f) return

        val sweep = Math.clamp(Math.abs(sweepDegrees), 0.0f, 360.0f)
        if (sweep <= 0.0f) return

        val start = if (sweepDegrees < 0.0f) startDegrees + sweepDegrees else startDegrees

        buffer.ensureCapacity(currentOffset + ARC_BYTES)
        buffer.tryMap()

        val halfStroke = strokeWidth * 0.5f
        // 包围盒外扩一像素，给 SDF 的抗锯齿过渡带留出空间。
        val extent = radius + halfStroke + 1.0f
        val x1 = centerX - extent
        val y1 = centerY - extent
        val x2 = centerX + extent
        val y2 = centerY + extent

        val startRadians = Math.toRadians(start.toDouble()).toFloat()
        val sweepRadians = if (sweep >= 360.0f) TAU else Math.toRadians(sweep.toDouble()).toFloat()
        val gradientRotationRadians = Math.toRadians(gradientRotationDegrees.toDouble()).toFloat()
        val cap = if (roundCap) 1.0f else 0.0f

        val startAbgr = ARGB.toABGR(startColor.getRGB())
        val middleAbgr = ARGB.toABGR(middleColor.getRGB())
        val endAbgr = ARGB.toABGR(endColor.getRGB())

        addVertex(
            x1, y1, centerX, centerY, radius, halfStroke, startRadians, sweepRadians, cap,
            gradientRotationRadians, startAbgr, middleAbgr, endAbgr
        )
        addVertex(
            x1, y2, centerX, centerY, radius, halfStroke, startRadians, sweepRadians, cap,
            gradientRotationRadians, startAbgr, middleAbgr, endAbgr
        )
        addVertex(
            x2, y2, centerX, centerY, radius, halfStroke, startRadians, sweepRadians, cap,
            gradientRotationRadians, startAbgr, middleAbgr, endAbgr
        )
        addVertex(
            x2, y1, centerX, centerY, radius, halfStroke, startRadians, sweepRadians, cap,
            gradientRotationRadians, startAbgr, middleAbgr, endAbgr
        )
    }

    private fun addVertex(
        vx: Float, vy: Float, centerX: Float, centerY: Float, radius: Float, halfStroke: Float,
        startRadians: Float, sweepRadians: Float, roundCap: Float, gradientRotationRadians: Float,
        startColor: Int, middleColor: Int, endColor: Int,
    ) {
        val baseAddr = MemoryUtil.memAddress(buffer.getMappedBuffer())
        val p = baseAddr + currentOffset
        MemoryUtil.memPutFloat(p, vx)
        MemoryUtil.memPutFloat(p + 4, vy)
        MemoryUtil.memPutFloat(p + 8, 0.0f)
        MemoryUtil.memPutInt(p + 12, startColor)
        MemoryUtil.memPutInt(p + 16, middleColor)
        MemoryUtil.memPutInt(p + 20, endColor)
        MemoryUtil.memPutFloat(p + 24, centerX)
        MemoryUtil.memPutFloat(p + 28, centerY)
        MemoryUtil.memPutFloat(p + 32, radius)
        MemoryUtil.memPutFloat(p + 36, halfStroke)
        MemoryUtil.memPutFloat(p + 40, startRadians)
        MemoryUtil.memPutFloat(p + 44, sweepRadians)
        MemoryUtil.memPutFloat(p + 48, roundCap)
        MemoryUtil.memPutFloat(p + 52, gradientRotationRadians)
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
            { "Arc Draw" }, colorView, Optional.empty<Vector4fc>(),
            info.depthView, OptionalDouble.empty()
        ).use { pass ->
            pass.setPipeline(RenderSystem.getCompiledPipeline(LuminRenderPipelines.ARC))
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
