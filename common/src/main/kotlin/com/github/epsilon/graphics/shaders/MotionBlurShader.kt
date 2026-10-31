package com.github.epsilon.graphics.shaders

import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.LuminBindGroupLayouts
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.VideoMode
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.CommandEncoder
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.FilterMode
import net.minecraft.client.renderer.DynamicGpuDataStorage
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Matrix4fc
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.roundToInt

object MotionBlurShader {

    private const val MAX_SAMPLES = 256
    private const val MAX_FRAME_TIME_SECONDS = 0.25
    private const val MAX_CAMERA_DELTA_SQUARED = 1024.0
    private val VERTEX_SHADER: Identifier = Identifier.withDefaultNamespace("core/screenquad")
    private val FRAGMENT_SHADER: Identifier = ResourceLocationUtils.getIdentifier("motion_blur")
    private val UNIFORMS_SIZE: Int = Std140SizeCalculator()
        .putMat4f()
        .putMat4f()
        .putMat4f()
        .putMat4f()
        .putVec4()
        .putVec4()
        .putIVec4()
        .get()

    private val previousView = Matrix4f()
    private val previousProjection = Matrix4f()
    private var previousCameraPosition: Vec3 = Vec3.ZERO
    private var pipeline: RenderPipeline? = null
    private var input: RenderTarget? = null
    private var previousFrameNanos: Long = 0L
    private var previousWidth: Int = 0
    private var previousHeight: Int = 0
    private var hasHistory: Boolean = false

    fun render(
        target: RenderTarget?,
        view: Matrix4fc,
        projection: Matrix4fc,
        cameraPosition: Vec3,
        settings: Settings
    ) {
        val now = System.nanoTime()
        val frameTime = if (previousFrameNanos == 0L) 0.0 else (now - previousFrameNanos) / 1_000_000_000.0
        val cameraDelta = cameraPosition.subtract(previousCameraPosition)
        val targetValid = target != null &&
                target.width > 0 &&
                target.height > 0 &&
                target.colorTexture != null &&
                target.colorTextureView != null &&
                target.depthTextureView != null
        val historyValid = hasHistory &&
                targetValid &&
                target != null &&
                target.width == previousWidth &&
                target.height == previousHeight &&
                frameTime > 0.0 &&
                frameTime <= MAX_FRAME_TIME_SECONDS &&
                cameraDelta.lengthSqr() <= MAX_CAMERA_DELTA_SQUARED

        if (historyValid && settings.render && settings.strength > 0.0f) {
            renderEffect(target, view, projection, cameraDelta, settings, frameTime)
        }

        previousView.set(view)
        previousProjection.set(projection)
        previousCameraPosition = cameraPosition
        previousFrameNanos = now
        if (targetValid) {
            val validTarget = target
            previousWidth = validTarget.width
            previousHeight = validTarget.height
            hasHistory = true
        } else {
            hasHistory = false
        }
    }

    private fun renderEffect(
        target: RenderTarget,
        view: Matrix4fc,
        projection: Matrix4fc,
        cameraDelta: Vec3,
        settings: Settings,
        frameTime: Double
    ) {
        ensureProgram()
        ensureInput(target.width, target.height)
        val inputTarget = input
        if (inputTarget == null || inputTarget.colorTexture == null || inputTarget.colorTextureView == null) return

        var frameScale = 1.0f
        if (settings.refreshRateScaling) {
            // 26.3 的 Window 不再直接暴露刷新率，需要从当前显示模式读取。
            val activeMode: VideoMode? = mc.window.activeVideoMode
            val refreshRate = if (activeMode == null) 0.0F else activeMode.refreshRate
            if (refreshRate > 0.0F) {
                frameScale = maxOf(1.0f, (1.0 / frameTime).toFloat() / refreshRate)
            }
        }

        val scaledStrength = settings.strength * frameScale
        val scaledSamples = (settings.samples * frameScale).roundToInt().coerceIn(4, MAX_SAMPLES)
        val uniformData = MotionBlurUniforms(
            Matrix4f(view).invert(),
            Matrix4f(projection).invert(),
            previousView,
            previousProjection,
            cameraDelta,
            target.width.toFloat(),
            target.height.toFloat(),
            scaledStrength,
            RenderSystem.getDevice().deviceInfo.isZZeroToOne,
            scaledSamples,
            settings.algorithm,
            settings.depthBlur
        )
        val uniforms: GpuBufferSlice = LuminRenderSystem.writeDynamicUniform(
            "motion_blur_data",
            "Epsilon Motion Blur UBO",
            UNIFORMS_SIZE,
            4,
            uniformData
        )

        val encoder: CommandEncoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.copyTextureToTexture(
            target.colorTexture!!,
            inputTarget.colorTexture!!,
            0, 0, 0, 0, 0,
            target.width, target.height
        )

        encoder.createRenderPass(
            { "Epsilon Motion Blur" },
            target.colorTextureView!!,
            Optional.empty()
        ).use { pass ->
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline!!))
            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform("MotionBlurData", uniforms)
            pass.setUniform(
                "InputSampler",
                inputTarget.colorTextureView,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            )
            pass.setUniform(
                "DepthSampler",
                target.depthTextureView,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
            )
            pass.draw(3, 1, 0, 0)
        }
    }

    private fun ensureProgram() {
        if (pipeline != null) return

        pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/motion_blur"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withVertexShader(VERTEX_SHADER)
            .withFragmentShader(FRAGMENT_SHADER)
            .withBindGroupLayout(LuminBindGroupLayouts.MOTION_BLUR_DATA)
            .withBindGroupLayout(LuminBindGroupLayouts.MOTION_BLUR_TEXTURES)
            .withCull(false)
            .build()
    }

    private fun ensureInput(width: Int, height: Int) {
        var inputTarget = input
        if (inputTarget == null) {
            inputTarget = TextureTarget("Epsilon Motion Blur Input", width, height, GpuFormat.RGBA8_UNORM, null)
            input = inputTarget
        } else if (inputTarget.width != width || inputTarget.height != height) {
            inputTarget.resize(width, height)
        }
    }

    fun resetHistory() {
        hasHistory = false
        previousFrameNanos = 0L
    }

    fun close() {
        val currentInput = input
        if (currentInput != null) {
            currentInput.destroyBuffers()
            input = null
        }
        resetHistory()
    }

    data class Settings(
        val strength: Float,
        val samples: Int,
        val algorithm: Int,
        val depthBlur: Boolean,
        val refreshRateScaling: Boolean,
        val render: Boolean
    )

    private data class MotionBlurUniforms(
        val viewInverse: Matrix4fc,
        val projectionInverse: Matrix4fc,
        val previousView: Matrix4fc,
        val previousProjection: Matrix4fc,
        val cameraDelta: Vec3,
        val width: Float,
        val height: Float,
        val strength: Float,
        val zZeroToOne: Boolean,
        val samples: Int,
        val algorithm: Int,
        val depthBlur: Boolean
    ) : DynamicGpuDataStorage.DynamicGpuData {
        override fun write(buffer: ByteBuffer) {
            Std140Builder.intoBuffer(buffer)
                .putMat4f(viewInverse)
                .putMat4f(projectionInverse)
                .putMat4f(previousView)
                .putMat4f(previousProjection)
                .putVec4(cameraDelta.x.toFloat(), cameraDelta.y.toFloat(), cameraDelta.z.toFloat(), 0.0f)
                .putVec4(width, height, strength, if (zZeroToOne) 1.0f else 0.0f)
                .putIVec4(samples, algorithm, if (depthBlur) 1 else 0, 0)
        }
    }
}
