package com.github.epsilon.graphics.shaders

import com.github.epsilon.Constants
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.LuminBindGroupLayouts
import com.github.epsilon.graphics.LuminRenderSystem
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
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
import java.nio.ByteBuffer
import java.util.*

/**
 * 整屏 FXAA 后处理，采样主渲染目标并写回，作为 {@code AntiAlias} 的 FXAA 模式使用。
 */
object FXAAShader {

    private val VERTEX_SHADER: Identifier = Identifier.withDefaultNamespace("core/screenquad")
    private val FRAGMENT_SHADER: Identifier = ResourceLocationUtils.getIdentifier("fxaa")

    private val UNIFORMS_SIZE: Int = Std140SizeCalculator()
        .putVec4()
        .get()

    private var pipeline: RenderPipeline? = null
    private var input: RenderTarget? = null

    private fun ensureProgram() {
        if (this.pipeline == null) {
            this.pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(ResourceLocationUtils.getIdentifier("pipeline/fxaa"))
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withVertexShader(VERTEX_SHADER)
                .withFragmentShader(FRAGMENT_SHADER)
                .withBindGroupLayout(LuminBindGroupLayouts.FXAA_INFO)
                .withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
                .withCull(false)
                .build()
        }
    }

    private fun ensureInput(framebuffer: RenderTarget) {
        val fbWidth = framebuffer.width
        val fbHeight = framebuffer.height

        var inputTarget = this.input
        if (inputTarget == null) {
            inputTarget = TextureTarget("Epsilon FXAA Input", fbWidth, fbHeight, GpuFormat.RGBA8_UNORM, null)
            this.input = inputTarget
        }

        if (inputTarget.width != fbWidth || inputTarget.height != fbHeight) {
            inputTarget.resize(fbWidth, fbHeight)
        }
    }

    fun renderMainTarget() {
        render(Constants.client.gameRenderer.mainRenderTarget())
    }

    fun render(framebuffer: RenderTarget?) {
        this.ensureProgram()

        if (framebuffer == null || framebuffer.width <= 0 || framebuffer.height <= 0) {
            return
        }

        if (framebuffer.colorTexture == null || framebuffer.colorTextureView == null) {
            return
        }

        this.ensureInput(framebuffer)

        val inputTarget = this.input
        if (inputTarget == null || inputTarget.colorTexture == null || inputTarget.colorTextureView == null) {
            return
        }

        val encoder: CommandEncoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.copyTextureToTexture(
            framebuffer.colorTexture!!,
            inputTarget.colorTexture!!,
            0, 0, 0, 0, 0,
            framebuffer.width, framebuffer.height
        )

        val fxaaInfo: GpuBufferSlice = LuminRenderSystem.writeDynamicUniform(
            "fxaa_info",
            "Epsilon FXAA UBO",
            UNIFORMS_SIZE,
            4,
            FXAAInfo(framebuffer.width.toFloat(), framebuffer.height.toFloat())
        )

        encoder.createRenderPass(
            { "Epsilon FXAA" },
            framebuffer.colorTextureView!!,
            Optional.empty()
        ).use { renderPass ->
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(this.pipeline!!))
            RenderSystem.bindDefaultUniforms(renderPass)
            renderPass.setUniform("FxaaInfo", fxaaInfo)
            renderPass.setUniform("InputSampler", inputTarget.colorTextureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            renderPass.draw(3, 1, 0, 0)
        }
    }

    /**
     * 释放 FXAA 的中间纹理；必须在渲染线程调用，重复调用安全。
     */
    fun close() {
        val currentInput = this.input
        if (currentInput != null) {
            currentInput.destroyBuffers()
            this.input = null
        }
    }

    private data class FXAAInfo(val width: Float, val height: Float) : DynamicGpuDataStorage.DynamicGpuData {

        override fun write(buffer: ByteBuffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec4(width, height, 1.0f / width, 1.0f / height)
        }
    }
}
