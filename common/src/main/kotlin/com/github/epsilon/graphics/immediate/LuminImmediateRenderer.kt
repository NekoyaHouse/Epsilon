package com.github.epsilon.graphics.immediate

import com.github.epsilon.Constants.mc
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.buffer.LuminRingBuffer
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.GpuTextureView
import com.mojang.renderpearl.api.vertex.VertexFormat
import com.mojang.renderpearl.api.vertex.VertexFormatElement
import net.minecraft.client.renderer.rendertype.TextureTransform
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.joml.Vector4fc
import org.lwjgl.system.MemoryUtil
import java.nio.ByteOrder
import java.util.*
import java.util.function.Consumer

object LuminImmediateRenderer {

    private const val DEFAULT_BUFFER_SIZE: Long = 1024L * 1024
    private val LITTLE_ENDIAN: Boolean = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN

    private val POS_COLOR_QUADS: Channel = Channel(DefaultVertexFormat.POSITION_COLOR, PrimitiveTopology.QUADS)
    private val POS_COLOR_TRIANGLE_STRIP: Channel =
        Channel(DefaultVertexFormat.POSITION_COLOR, PrimitiveTopology.TRIANGLE_STRIP)

    private val POS_COLOR_TRIANGLE_FAN: Channel =
        Channel(DefaultVertexFormat.POSITION_COLOR, PrimitiveTopology.TRIANGLE_FAN)

    private val POS_TEX_COLOR_QUADS: Channel =
        Channel(DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS)

    private val POS_COLOR_NORMAL_LINE_WIDTH_LINES: Channel =
        Channel(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, PrimitiveTopology.LINES)

    fun beginPosColorQuads(pipeline: RenderPipeline): PosColorQuads {
        return beginPosColorQuads(pipeline, null)
    }

    fun beginPosColorQuads(pipeline: RenderPipeline, passConfigurer: Consumer<RenderPass>?): PosColorQuads {
        return PosColorQuads(POS_COLOR_QUADS.begin(pipeline, null, passConfigurer))
    }

    fun beginPosColorTriangleStrip(pipeline: RenderPipeline): PosColorTriangleStrip {
        return PosColorTriangleStrip(POS_COLOR_TRIANGLE_STRIP.begin(pipeline, null))
    }

    fun beginPosColorTriangleFan(pipeline: RenderPipeline): PosColorTriangleFan {
        return PosColorTriangleFan(POS_COLOR_TRIANGLE_FAN.begin(pipeline, null))
    }

    fun beginPosTexColorQuads(pipeline: RenderPipeline, texture: Identifier): PosTexColorQuads {
        return PosTexColorQuads(POS_TEX_COLOR_QUADS.begin(pipeline, texture))
    }

    fun beginLines(pipeline: RenderPipeline): Lines {
        return Lines(POS_COLOR_NORMAL_LINE_WIDTH_LINES.begin(pipeline, null))
    }

    fun endFrame() {
        POS_COLOR_QUADS.endFrame()
        POS_COLOR_TRIANGLE_STRIP.endFrame()
        POS_COLOR_TRIANGLE_FAN.endFrame()
        POS_TEX_COLOR_QUADS.endFrame()
        POS_COLOR_NORMAL_LINE_WIDTH_LINES.endFrame()
    }

    /**
     * 批次句柄的公共部分：持有共享通道，并负责在结束时提交本批次。
     */
    abstract class Batch protected constructor(protected val channel: Channel) {
        fun end() {
            channel.drawAndReset()
        }
    }

    open class PosColorQuads internal constructor(channel: Channel) : Batch(channel) {
        fun vertex(matrix: Matrix4f, x: Float, y: Float, z: Float, color: Int) {
            channel.putPosition(matrix, x, y, z)
            channel.putColor(color)
            channel.finishVertex()
        }
    }

    open class PosColorTriangleStrip internal constructor(channel: Channel) : Batch(channel) {
        fun vertex(matrix: Matrix4f, x: Float, y: Float, z: Float, color: Int) {
            channel.putPosition(matrix, x, y, z)
            channel.putColor(color)
            channel.finishVertex()
        }
    }

    open class PosColorTriangleFan internal constructor(channel: Channel) : Batch(channel) {
        fun vertex(matrix: Matrix4f, x: Float, y: Float, z: Float, color: Int) {
            channel.putPosition(matrix, x, y, z)
            channel.putColor(color)
            channel.finishVertex()
        }
    }

    open class PosTexColorQuads internal constructor(channel: Channel) : Batch(channel) {
        fun vertex(matrix: Matrix4f, x: Float, y: Float, z: Float, u: Float, v: Float, color: Int) {
            channel.putPosition(matrix, x, y, z)
            channel.putUv(u, v)
            channel.putColor(color)
            channel.finishVertex()
        }
    }

    open class Lines internal constructor(channel: Channel) : Batch(channel) {
        private val normalTmp = Vector3f()

        fun vertex(
            matrix: Matrix4f,
            pose: PoseStack.Pose,
            x: Float,
            y: Float,
            z: Float,
            color: Int,
            nx: Float,
            ny: Float,
            nz: Float,
            width: Float,
        ) {
            channel.putPosition(matrix, x, y, z)
            channel.putColor(color)
            pose.transformNormal(nx, ny, nz, normalTmp).normalize()
            channel.putNormal(normalTmp.x, normalTmp.y, normalTmp.z)
            channel.putLineWidth(width)
            channel.finishVertex()
        }
    }

    class Channel internal constructor(format: VertexFormat, mode: PrimitiveTopology) {
        private val ringBuffer: LuminRingBuffer = LuminRingBuffer(DEFAULT_BUFFER_SIZE, GpuBuffer.USAGE_VERTEX)
        private val mode: PrimitiveTopology = mode
        private val stride: Int = format.vertexSize

        private val positionOffset: Int
        private val colorOffset: Int
        private val uvOffset: Int
        private val normalOffset: Int
        private val lineWidthOffset: Int

        private val posTmp = Vector3f()

        private var building = false
        private var currentOffset = 0L
        private var frameOffset = 0L
        private var batchStartOffset = 0L
        private var vertexCount = 0
        private var frameUsed = false

        private var vertexBaseAddr = 0L

        private var pipeline: RenderPipeline? = null

        private var texture: Identifier? = null
        private var passConfigurer: Consumer<RenderPass>? = null

        init {
            this.positionOffset = resolveOffset(format, DefaultVertexFormat.POSITION_SEMANTIC_NAME)
            this.colorOffset = resolveOffset(format, DefaultVertexFormat.COLOR_SEMANTIC_NAME)
            this.uvOffset = resolveOffset(format, DefaultVertexFormat.UV0_SEMANTIC_NAME)
            this.normalOffset = resolveOffset(format, DefaultVertexFormat.NORMAL_SEMANTIC_NAME)
            this.lineWidthOffset = resolveOffset(format, DefaultVertexFormat.LINE_WIDTH_SEMANTIC_NAME)
        }

        internal fun begin(pipeline: RenderPipeline, texture: Identifier?): Channel {
            return begin(pipeline, texture, null)
        }

        internal fun begin(
            pipeline: RenderPipeline,
            texture: Identifier?,
            passConfigurer: Consumer<RenderPass>?
        ): Channel {
            if (building) {
                throw IllegalStateException("Immediate channel is already building")
            }
            building = true
            currentOffset = frameOffset
            batchStartOffset = frameOffset
            vertexCount = 0
            this.pipeline = pipeline
            this.texture = texture
            this.passConfigurer = passConfigurer
            ringBuffer.tryMap()
            return this
        }

        internal fun putPosition(matrix: Matrix4f, x: Float, y: Float, z: Float) {
            if (positionOffset < 0 || !ensureCapacity()) {
                return
            }
            matrix.transformPosition(x, y, z, posTmp)
            val p = vertexBaseAddr + positionOffset
            MemoryUtil.memPutFloat(p, posTmp.x)
            MemoryUtil.memPutFloat(p + 4L, posTmp.y)
            MemoryUtil.memPutFloat(p + 8L, posTmp.z)
        }

        internal fun putColor(color: Int) {
            if (colorOffset < 0 || !ensureCapacity()) {
                return
            }
            val abgr = ARGB.toABGR(color)
            val p = vertexBaseAddr + colorOffset
            MemoryUtil.memPutInt(p, if (LITTLE_ENDIAN) abgr else Integer.reverseBytes(abgr))
        }

        internal fun putUv(u: Float, v: Float) {
            if (uvOffset < 0 || !ensureCapacity()) {
                return
            }
            val p = vertexBaseAddr + uvOffset
            MemoryUtil.memPutFloat(p, u)
            MemoryUtil.memPutFloat(p + 4L, v)
        }

        internal fun putNormal(nx: Float, ny: Float, nz: Float) {
            if (normalOffset < 0 || !ensureCapacity()) {
                return
            }
            val p = vertexBaseAddr + normalOffset
            MemoryUtil.memPutByte(p, packNormal(nx))
            MemoryUtil.memPutByte(p + 1L, packNormal(ny))
            MemoryUtil.memPutByte(p + 2L, packNormal(nz))
        }

        internal fun putLineWidth(width: Float) {
            if (lineWidthOffset < 0 || !ensureCapacity()) {
                return
            }
            MemoryUtil.memPutFloat(vertexBaseAddr + lineWidthOffset, width)
        }

        internal fun finishVertex() {
            if (!building || vertexBaseAddr == 0L) {
                return
            }
            val completedVertexBaseAddr = vertexBaseAddr
            currentOffset += stride
            vertexCount++

            if (mode == PrimitiveTopology.LINES) {
                val duplicateVertexBaseAddr = MemoryUtil.memAddress(ringBuffer.getMappedBuffer()) + currentOffset
                MemoryUtil.memCopy(completedVertexBaseAddr, duplicateVertexBaseAddr, stride.toLong())
                currentOffset += stride
                vertexCount++
            }

            vertexBaseAddr = 0L
        }

        internal fun ensureCapacity(): Boolean {
            if (!building) {
                return false
            }
            if (vertexBaseAddr != 0L) {
                return true
            }
            val requiredBytes = if (mode == PrimitiveTopology.LINES) stride * 2L else stride.toLong()
            ringBuffer.ensureCapacity(currentOffset + requiredBytes)
            if (!ringBuffer.isMapped()) {
                ringBuffer.tryMap()
            }
            vertexBaseAddr = MemoryUtil.memAddress(ringBuffer.getMappedBuffer()) + currentOffset
            return true
        }

        internal fun drawAndReset() {
            var submittedDraw = false
            val completedOffset = currentOffset

            try {
                if (vertexCount <= 0) {
                    return
                }

                if (ringBuffer.isMapped()) {
                    ringBuffer.unmap()
                }

                val colorView: GpuTextureView? = LuminRenderSystem.resolveColorView()
                val depthView: GpuTextureView? = LuminRenderSystem.resolveDepthView()

                if (colorView == null) {
                    return
                }

                val pipeline = this.pipeline
                if (depthView == null && pipeline != null && pipeline.depthStencilState != null) {
                    return
                }

                val dynamicUniforms: GpuBufferSlice = RenderSystem.getDynamicUniforms().writeTransform(
                    RenderSystem.getModelViewMatrixCopy(),
                    Vector4f(1f, 1f, 1f, 1f),
                    Vector3f(0f, 0f, 0f),
                    TextureTransform.DEFAULT_TEXTURING.createMatrix()
                )

                val texture = this.texture
                val textureObject: AbstractTexture? =
                    if (texture == null) null else mc.textureManager.getTexture(texture)

                RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    { "Lumin Immediate Draw" },
                    colorView, Optional.empty<Vector4fc>(),
                    depthView, OptionalDouble.empty()
                ).use { pass ->
                    pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline!!))
                    RenderSystem.bindDefaultUniforms(pass)
                    pass.setUniform("DynamicTransforms", dynamicUniforms)
                    pass.setVertexBuffer(0, ringBuffer.getGpuBuffer().slice())

                    if (textureObject != null) {
                        pass.setUniform("Sampler0", textureObject.textureView, textureObject.sampler)
                    }

                    val passConfigurer = this.passConfigurer
                    if (passConfigurer != null) {
                        passConfigurer.accept(pass)
                    }

                    val firstVertex = Math.toIntExact(batchStartOffset / stride)

                    when (mode) {
                        PrimitiveTopology.LINES, PrimitiveTopology.QUADS -> {
                            val indexCount = mode.indexCount(vertexCount)
                            if (indexCount > 0) {
                                val autoIndices = RenderSystem.getSequentialBuffer(mode)
                                val ibo: GpuBuffer = autoIndices.getBuffer(indexCount)
                                pass.setIndexBuffer(ibo, autoIndices.type())
                                pass.drawIndexed(indexCount, 1, 0, firstVertex, 0)
                                submittedDraw = true
                            }
                        }

                        else -> {
                            pass.draw(vertexCount, 1, firstVertex, 0)
                            submittedDraw = true
                        }
                    }
                }
            } finally {
                if (ringBuffer.isMapped()) {
                    ringBuffer.unmap()
                }

                if (submittedDraw) {
                    frameUsed = true
                    frameOffset = completedOffset
                }

                building = false
                currentOffset = frameOffset
                batchStartOffset = frameOffset
                vertexCount = 0
                vertexBaseAddr = 0L
                pipeline = null
                texture = null
                passConfigurer = null
            }
        }

        internal fun endFrame() {
            if (ringBuffer.isMapped()) {
                ringBuffer.unmap()
            }

            if (frameUsed) {
                ringBuffer.rotate()
            }

            frameUsed = false
            frameOffset = 0L
            currentOffset = 0L
            batchStartOffset = 0L
        }

        companion object {
            private fun resolveOffset(format: VertexFormat, semanticName: String): Int {
                val element: VertexFormatElement? = format.getElement(semanticName)
                return element?.offset() ?: -1
            }

            private fun packNormal(value: Float): Byte {
                val clamped = value.coerceIn(-1.0f, 1.0f)
                return ((clamped * 127.0f).toInt() and 0xFF).toByte()
            }
        }
    }

}
