package com.github.epsilon.graphics.shaders

import com.github.epsilon.Constants.mc
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
import java.awt.Color
import java.nio.ByteBuffer
import java.util.*

object FilterShader {

    private val UNIFORMS_SIZE: Int = Std140SizeCalculator()
        .putVec4()
        .get()

    private var pipeline: RenderPipeline? = null
    private var input: RenderTarget? = null

    private fun ensureProgram() {
        if (this.pipeline == null) {
            this.pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(ResourceLocationUtils.getIdentifier("pipeline/filter"))
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(ResourceLocationUtils.getIdentifier("filter"))
                .withBindGroupLayout(LuminBindGroupLayouts.FILTER_COLOR)
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
            inputTarget = TextureTarget("Epsilon Filter Input", fbWidth, fbHeight, GpuFormat.RGBA8_UNORM, null)
            this.input = inputTarget
        }

        if (inputTarget.width != fbWidth || inputTarget.height != fbHeight) {
            inputTarget.resize(fbWidth, fbHeight)
        }
    }

    fun renderToMainTarget(color: Color?) {
        render(mc.gameRenderer.mainRenderTarget(), color)
    }

    fun render(framebuffer: RenderTarget?, color: Color?) {
        this.ensureProgram()

        if (framebuffer == null || color == null || framebuffer.width <= 0 || framebuffer.height <= 0) {
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

        val filterColor: GpuBufferSlice = LuminRenderSystem.writeDynamicUniform(
            "filter_color",
            "Epsilon Filter UBO",
            UNIFORMS_SIZE,
            4,
            FilterColor(color)
        )

        encoder.createRenderPass(
            { "Epsilon Filter" },
            framebuffer.colorTextureView!!,
            Optional.empty()
        ).use { renderPass ->
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(this.pipeline!!))
            RenderSystem.bindDefaultUniforms(renderPass)
            renderPass.setUniform("FilterColor", filterColor)
            renderPass.setUniform(
                "InputSampler",
                inputTarget.colorTextureView,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            )
            renderPass.draw(3, 1, 0, 0)
        }
    }

    private data class FilterColor(val red: Float, val green: Float, val blue: Float, val alpha: Float) :
        DynamicGpuDataStorage.DynamicGpuData {

        constructor(color: Color) : this(
            color.red / 255.0f,
            color.green / 255.0f,
            color.blue / 255.0f,
            color.alpha / 255.0f
        )

        override fun write(buffer: ByteBuffer) {
            Std140Builder.intoBuffer(buffer).putVec4(red, green, blue, alpha)
        }
    }
}
