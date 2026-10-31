package com.github.epsilon.graphics.shaders

import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.LuminBindGroupLayouts
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.modules.impl.render.CustomSky
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
import net.minecraft.util.Util
import java.awt.Color
import java.nio.ByteBuffer
import java.util.*

object CustomSkyShader {

    private val VERTEX_SHADER: Identifier = Identifier.withDefaultNamespace("core/screenquad")
    private val UNIFORMS_SIZE: Int = Std140SizeCalculator()
        .putVec4()
        .putVec4()
        .putVec4()
        .get()

    private val pipelines: EnumMap<CustomSky.ShaderMode, RenderPipeline> = EnumMap(CustomSky.ShaderMode::class.java)
    private var input: TextureTarget? = null

    fun render(target: RenderTarget?, module: CustomSky?) {
        if (target == null || module == null || target.width <= 0 || target.height <= 0 || target.colorTexture == null || target.colorTextureView == null) {
            return
        }

        val mode: CustomSky.ShaderMode = module.shader
        val pipeline: RenderPipeline = getPipeline(mode)
        val encoder: CommandEncoder = RenderSystem.getDevice().createCommandEncoder()
        if (mode == CustomSky.ShaderMode.Local) {
            ensureInput(target)
            val inputTarget = input
            if (inputTarget == null || inputTarget.colorTexture == null || inputTarget.colorTextureView == null) {
                return
            }

            encoder.copyTextureToTexture(
                target.colorTexture!!, inputTarget.colorTexture!!,
                0, 0, 0, 0, 0,
                target.width, target.height
            )
        }

        val color: Color = module.color
        val backgroundColor: Color = module.backgroundColor
        val time = (Util.getMillis() % 100_000L) * module.speed.toFloat() * 0.0008f
        val uniforms: GpuBufferSlice = LuminRenderSystem.writeDynamicUniform(
            "custom_sky",
            "Epsilon Custom Sky UBO",
            UNIFORMS_SIZE,
            16,
            SkyUniforms(color, backgroundColor, target.width, target.height, time)
        )

        encoder.createRenderPass(
            { "Epsilon Custom Sky" },
            target.colorTextureView!!,
            Optional.empty()
        ).use { renderPass ->
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline))
            RenderSystem.bindDefaultUniforms(renderPass)
            renderPass.setUniform("CustomSky", uniforms)
            if (mode == CustomSky.ShaderMode.Local) {
                renderPass.setUniform("InputSampler", input!!.colorTextureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            }
            renderPass.draw(3, 1, 0, 0)
        }
    }

    private fun getPipeline(mode: CustomSky.ShaderMode): RenderPipeline {
        return pipelines.computeIfAbsent(mode) { shaderMode ->
            val builder: RenderPipeline.Builder = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(ResourceLocationUtils.getIdentifier("pipeline/custom_sky_" + shaderMode.name.lowercase()))
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withVertexShader(VERTEX_SHADER)
                .withFragmentShader(ResourceLocationUtils.getIdentifier("custom_sky_" + shaderMode.name.lowercase()))
                .withBindGroupLayout(LuminBindGroupLayouts.CUSTOM_SKY)
                .withCull(false)
            if (shaderMode == CustomSky.ShaderMode.Local) {
                builder.withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
            }
            builder.build()
        }
    }

    private fun ensureInput(target: RenderTarget) {
        var inputTarget = input
        if (inputTarget == null) {
            inputTarget = TextureTarget("Epsilon Custom Sky Input", target.width, target.height, GpuFormat.RGBA8_UNORM, null)
            input = inputTarget
        } else if (inputTarget.width != target.width || inputTarget.height != target.height) {
            inputTarget.resize(target.width, target.height)
        }
    }

    private data class SkyUniforms(
        val red: Float, val green: Float, val blue: Float, val alpha: Float,
        val backgroundRed: Float, val backgroundGreen: Float, val backgroundBlue: Float, val backgroundAlpha: Float,
        val width: Float, val height: Float, val time: Float
    ) : DynamicGpuDataStorage.DynamicGpuData {
        constructor(color: Color, backgroundColor: Color, width: Int, height: Int, time: Float) : this(
            color.red / 255.0f, color.green / 255.0f, color.blue / 255.0f, color.alpha / 255.0f,
            backgroundColor.red / 255.0f, backgroundColor.green / 255.0f,
            backgroundColor.blue / 255.0f, backgroundColor.alpha / 255.0f,
            width.toFloat(), height.toFloat(), time
        )

        override fun write(buffer: ByteBuffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec4(red, green, blue, alpha)
                .putVec4(backgroundRed, backgroundGreen, backgroundBlue, backgroundAlpha)
                .putVec4(width, height, time, 0.0f)
        }
    }
}
