package com.github.epsilon.graphics.shaders

import com.github.epsilon.Constants
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.LuminBindGroupLayouts
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.CommandEncoder
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.renderer.DynamicGpuDataStorage
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import org.joml.Matrix4f
import java.nio.ByteBuffer
import java.util.Optional

class BlurShader {

    companion object {
        @JvmField
        val INSTANCE: BlurShader = BlurShader()

        private const val MAX_SEGMENTS = 64

        private val BLUR_PATH: Identifier = ResourceLocationUtils.getIdentifier("blur")
        private val BLUR_3D_BOX_PATH: Identifier = ResourceLocationUtils.getIdentifier("blur_3d_box")

        private val UNIFORMS_SIZE: Int = blurUniformsSize()

        private val BOX_UNIFORMS_SIZE: Int = Std140SizeCalculator().putVec4().get()

        private fun blurUniformsSize(): Int {
            val calculator = Std140SizeCalculator().putVec3().putVec4().putVec4().putVec4()
            for (i in 0 until MAX_SEGMENTS * 2) {
                calculator.putVec4()
            }
            return calculator.get()
        }

        private fun clampSegmentCount(segmentRects: FloatArray?, segmentCount: Int): Int {
            if (segmentRects == null || segmentCount <= 0) return 0
            return Math.min(MAX_SEGMENTS, Math.min(segmentCount, segmentRects.size / 4))
        }
    }

    private var pipeline: RenderPipeline? = null
    private var boxPipeline: RenderPipeline? = null
    private var input: RenderTarget? = null

    private fun ensureProgram() {
        if (this.pipeline == null) {
            this.pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(ResourceLocationUtils.getIdentifier("pipeline/blur"))
                .withVertexShader(BLUR_PATH)
                .withFragmentShader(BLUR_PATH)
                .withBindGroupLayout(LuminBindGroupLayouts.BLUR)
                .withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .build()
        }
    }

    private fun ensureBoxProgram() {
        if (this.boxPipeline == null) {
            this.boxPipeline = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(ResourceLocationUtils.getIdentifier("pipeline/blur_3d_box"))
                .withVertexShader(BLUR_3D_BOX_PATH)
                .withFragmentShader(BLUR_3D_BOX_PATH)
                .withBindGroupLayout(LuminBindGroupLayouts.BOX_BLUR)
                .withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withCull(false)
                .build()
        }
    }

    fun render(x: Float, y: Float, width: Float, height: Float, rTL: Float, rTR: Float, rBR: Float, rBL: Float, blurStrength: Float) {
        render(null, x, y, width, height, rTL, rTR, rBR, rBL, blurStrength, null, null, 0)
    }

    fun render(x: Float, y: Float, width: Float, height: Float, rTL: Float, rTR: Float, rBR: Float, rBL: Float, blurStrength: Float, segmentRects: FloatArray?, segmentRadii: FloatArray?, segmentCount: Int) {
        render(null, x, y, width, height, rTL, rTR, rBR, rBL, blurStrength, segmentRects, segmentRadii, segmentCount)
    }

    fun render(source: LuminRenderSystem.LuminRenderTarget?, x: Float, y: Float, width: Float, height: Float, radius: Float, blurStrength: Float) {
        render(source, x, y, width, height, radius, radius, radius, radius, blurStrength, null, null, 0)
    }

    fun render(source: LuminRenderSystem.LuminRenderTarget?, x: Float, y: Float, width: Float, height: Float, rTL: Float, rTR: Float, rBR: Float, rBL: Float, blurStrength: Float, segmentRects: FloatArray?, segmentRadii: FloatArray?, segmentCount: Int) {
        this.ensureProgram()

        if (width <= 0.0f || height <= 0.0f) {
            return
        }

        val mainTarget = Constants.mc.gameRenderer.mainRenderTarget()
        val activeTarget = LuminRenderSystem.getActiveTarget()
        val sourceTexture: GpuTexture? = if (source == null) mainTarget.colorTexture else source.colorTexture()
        val sourceWidth: Int = if (source == null) mainTarget.width else source.width()
        val sourceHeight: Int = if (source == null) mainTarget.height else source.height()
        val targetTexture: GpuTexture? = if (activeTarget == null) mainTarget.colorTexture else activeTarget.colorTexture()
        val targetView: GpuTextureView? = if (activeTarget == null) mainTarget.colorTextureView else activeTarget.colorView()
        val targetWidth: Int = if (activeTarget == null) mainTarget.width else activeTarget.width()
        val targetHeight: Int = if (activeTarget == null) mainTarget.height else activeTarget.height()

        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0 || sourceTexture == null || targetTexture == null || targetView == null) {
            return
        }

        var inputTarget = input
        if (inputTarget == null) {
            inputTarget = TextureTarget("Lumin Blur Input", sourceWidth, sourceHeight, GpuFormat.RGBA8_UNORM, null)
            input = inputTarget
        }

        if (inputTarget.width != sourceWidth || inputTarget.height != sourceHeight) {
            inputTarget.resize(sourceWidth, sourceHeight)
        }

        if (inputTarget.colorTexture == null || inputTarget.colorTextureView == null) {
            return
        }

        val scale = LuminRenderSystem.getGuiScale().toFloat()
        val pxX = x * scale
        val pxY = targetHeight - (y + height) * scale
        val pxW = width * scale
        val pxH = height * scale

        val rTLPx = Math.max(0.0f, rTL * scale)
        val rTRPx = Math.max(0.0f, rTR * scale)
        val rBRPx = Math.max(0.0f, rBR * scale)
        val rBLPx = Math.max(0.0f, rBL * scale)

        val quality = Math.max(0.0f, blurStrength)
        val count = clampSegmentCount(segmentRects, segmentCount)

        val encoder: CommandEncoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.copyTextureToTexture(
            sourceTexture,
            inputTarget.colorTexture!!,
            0, 0, 0, 0, 0,
            sourceWidth, sourceHeight
        )

        val blurUniforms: GpuBufferSlice = LuminRenderSystem.writeDynamicUniform(
            "blur_uniforms",
            "Lumin Blur UBO",
            UNIFORMS_SIZE,
            16,
            BlurUniforms(
                sourceWidth.toFloat(), sourceHeight.toFloat(), quality,
                pxW, pxH, pxX, pxY,
                rTLPx, rTRPx, rBRPx, rBLPx,
                scale, targetHeight.toFloat(), segmentRects, segmentRadii, count
            )
        )

        encoder.createRenderPass(
            { "Lumin Blur" },
            targetView,
            Optional.empty()
        ).use { renderPass ->
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline!!))
            RenderSystem.bindDefaultUniforms(renderPass)
            renderPass.setUniform("BlurUniforms", blurUniforms)
            renderPass.setUniform("InputSampler", inputTarget.colorTextureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            renderPass.draw(3, 1, 0, 0)
        }
    }

    fun render(x: Float, y: Float, width: Float, height: Float, radius: Float, blurStrength: Float) {
        render(x, y, width, height, radius, radius, radius, radius, blurStrength)
    }

    fun render(x: Float, y: Float, width: Float, height: Float, radius: Float, blurStrength: Float, segmentRects: FloatArray?, segmentRadii: FloatArray?, segmentCount: Int) {
        render(x, y, width, height, radius, radius, radius, radius, blurStrength, segmentRects, segmentRadii, segmentCount)
    }

    fun render3DBox(box: AABB, blurStrength: Double) {
        this.ensureBoxProgram()

        val fb: RenderTarget = Constants.mc.gameRenderer.mainRenderTarget()
        if (fb.width <= 0 || fb.height <= 0) {
            return
        }

        if (fb.colorTexture == null || fb.colorTextureView == null) {
            return
        }

        var inputTarget = input
        if (inputTarget == null) {
            inputTarget = TextureTarget("Lumin Blur Input", fb.width, fb.height, GpuFormat.RGBA8_UNORM, null)
            input = inputTarget
        }

        if (inputTarget.width != fb.width || inputTarget.height != fb.height) {
            inputTarget.resize(fb.width, fb.height)
        }

        if (inputTarget.colorTexture == null || inputTarget.colorTextureView == null) {
            return
        }

        val encoder: CommandEncoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.copyTextureToTexture(
            fb.colorTexture!!,
            inputTarget.colorTexture!!,
            0, 0, 0, 0, 0,
            fb.width, fb.height
        )

        val quality = Math.max(0.0f, blurStrength.toFloat())
        val boxBlurUniforms: GpuBufferSlice = LuminRenderSystem.writeDynamicUniform(
            "box_blur_uniforms",
            "Lumin 3D Box Blur UBO",
            BOX_UNIFORMS_SIZE,
            16,
            BoxBlurUniforms(fb.width.toFloat(), fb.height.toFloat(), quality)
        )

        val renderer: LuminImmediateRenderer.PosColorQuads = LuminImmediateRenderer.beginPosColorQuads(boxPipeline!!) { pass ->
            pass.setUniform("BoxBlurUniforms", boxBlurUniforms)
            pass.setUniform("InputSampler", inputTarget.colorTextureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
        }
        addBoxVertices(renderer, box)
        renderer.end()
    }

    private fun addBoxVertices(renderer: LuminImmediateRenderer.PosColorQuads, box: AABB) {
        val camPos = Constants.mc.entityRenderDispatcher.camera!!.position()

        val minX = (box.minX - camPos.x).toFloat()
        val minY = (box.minY - camPos.y).toFloat()
        val minZ = (box.minZ - camPos.z).toFloat()
        val maxX = (box.maxX - camPos.x).toFloat()
        val maxY = (box.maxY - camPos.y).toFloat()
        val maxZ = (box.maxZ - camPos.z).toFloat()

        val matrix: Matrix4f = Constants.mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix

        vertex(renderer, matrix, minX, minY, minZ)
        vertex(renderer, matrix, minX, minY, maxZ)
        vertex(renderer, matrix, maxX, minY, maxZ)
        vertex(renderer, matrix, maxX, minY, minZ)

        vertex(renderer, matrix, minX, maxY, minZ)
        vertex(renderer, matrix, maxX, maxY, minZ)
        vertex(renderer, matrix, maxX, maxY, maxZ)
        vertex(renderer, matrix, minX, maxY, maxZ)

        vertex(renderer, matrix, minX, minY, minZ)
        vertex(renderer, matrix, minX, maxY, minZ)
        vertex(renderer, matrix, maxX, maxY, minZ)
        vertex(renderer, matrix, maxX, minY, minZ)

        vertex(renderer, matrix, maxX, minY, minZ)
        vertex(renderer, matrix, maxX, maxY, minZ)
        vertex(renderer, matrix, maxX, maxY, maxZ)
        vertex(renderer, matrix, maxX, minY, maxZ)

        vertex(renderer, matrix, minX, minY, maxZ)
        vertex(renderer, matrix, maxX, minY, maxZ)
        vertex(renderer, matrix, maxX, maxY, maxZ)
        vertex(renderer, matrix, minX, maxY, maxZ)

        vertex(renderer, matrix, minX, minY, minZ)
        vertex(renderer, matrix, minX, minY, maxZ)
        vertex(renderer, matrix, minX, maxY, maxZ)
        vertex(renderer, matrix, minX, maxY, minZ)
    }

    private fun vertex(renderer: LuminImmediateRenderer.PosColorQuads, matrix: Matrix4f, x: Float, y: Float, z: Float) {
        renderer.vertex(matrix, x, y, z, -1)
    }

    @JvmRecord
    private data class BlurUniforms(
        val width: Float,
        val height: Float,
        val quality: Float,
        val rectWidth: Float,
        val rectHeight: Float,
        val rectX: Float,
        val rectY: Float,
        val radiusTopLeft: Float,
        val radiusTopRight: Float,
        val radiusBottomRight: Float,
        val radiusBottomLeft: Float,
        val scale: Float,
        val targetHeight: Float,
        val segmentRects: FloatArray?,
        val segmentRadii: FloatArray?,
        val segmentCount: Int
    ) : DynamicGpuDataStorage.DynamicGpuData {
        override fun write(buffer: ByteBuffer) {
            val builder = Std140Builder.intoBuffer(buffer)
                .putVec3(width, height, quality)
                .putVec4(rectWidth, rectHeight, rectX, rectY)
                .putVec4(radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft)
                .putVec4(segmentCount.toFloat(), 0.0f, 0.0f, 0.0f)

            for (i in 0 until MAX_SEGMENTS) {
                if (i < segmentCount) {
                    val rects = segmentRects!!
                    val offset = i * 4
                    val segmentX = rects[offset]
                    val segmentY = rects[offset + 1]
                    val segmentWidth = rects[offset + 2]
                    val segmentHeight = rects[offset + 3]
                    builder.putVec4(segmentX * scale, targetHeight - (segmentY + segmentHeight) * scale, segmentWidth * scale, segmentHeight * scale)
                } else {
                    builder.putVec4(0.0f, 0.0f, 0.0f, 0.0f)
                }
            }

            for (i in 0 until MAX_SEGMENTS) {
                val radii = segmentRadii
                val radius = if (radii != null && i < segmentCount && i < radii.size) {
                    Math.max(0.0f, radii[i] * scale)
                } else {
                    0.0f
                }
                builder.putVec4(radius, 0.0f, 0.0f, 0.0f)
            }
        }
    }

    @JvmRecord
    private data class BoxBlurUniforms(
        val width: Float, val height: Float, val quality: Float
    ) : DynamicGpuDataStorage.DynamicGpuData {
        override fun write(buffer: ByteBuffer) {
            Std140Builder.intoBuffer(buffer).putVec4(width, height, quality, 0.0f)
        }
    }
}
