package com.github.epsilon.graphics.shaders

import net.minecraft.client.renderer.RenderPassLayouts
import com.github.epsilon.graphics.LuminRenderPipelines
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.LuminBindGroupLayouts
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.LuminRenderSystem.LuminRenderTarget
import com.github.epsilon.modules.impl.render.HandView
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.*
import net.minecraft.client.renderer.DynamicGpuDataStorage
import java.nio.ByteBuffer
import java.util.*

object HandOpacityShader {

    private val UNIFORMS_SIZE = Std140SizeCalculator().putVec4().get()
    private var background: LuminRenderTarget? = null
    private var pipeline: RenderPipeline? = null

    fun captureBackground(target: RenderTarget) {
        if (!HandView.isEnabled || HandView.opacity == 100) return

        val input = background ?: LuminRenderTarget.create("hand-opacity", target.width, target.height)
            .also { background = it }
        input.resize(target.width, target.height)
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
            target.colorTexture!!, input.colorTexture(),
            0, 0, 0, 0, 0, target.width, target.height
        )
    }

    fun render(target: RenderTarget) {
        if (!HandView.isEnabled || HandView.opacity == 100) return
        val input = background ?: return

        val program = pipeline ?: RenderPipeline.builder(LuminRenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/hand_opacity"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("hand_opacity"))
            .withColorTargetState(
                ColorTargetState(
                    BlendFunction(
                        BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA,
                        BlendFactor.ZERO, BlendFactor.ONE
                    )
                )
            )
            .withBindGroupLayout(
                BindGroupLayout.builder()
                    .withUniform("HandOpacity", UniformType.UNIFORM_BUFFER).build()
            )
            .withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
            .withCull(false)
            .build().also { pipeline = it }
        val uniforms = LuminRenderSystem.writeDynamicUniform(
            "hand_opacity", "Epsilon Hand Opacity UBO", UNIFORMS_SIZE, 4,
            HandOpacity(HandView.opacity / 100.0f)
        )
        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { "Epsilon Hand Opacity" },
            RenderPassLayouts.COLOR_ONLY,
            target.colorTextureView!!, Optional.empty()
        ).use { pass ->
            pass.setPipeline(RenderSystem.getCompiledPipeline(program, pass.activeSubpass()))
            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform("HandOpacity", uniforms)
            pass.setUniform("InputSampler", input.colorView(), input.sampler())
            pass.draw(3, 1, 0, 0)
        }
    }

    private data class HandOpacity(val opacity: Float) : DynamicGpuDataStorage.DynamicGpuData {
        override fun write(buffer: ByteBuffer) {
            Std140Builder.intoBuffer(buffer).putVec4(opacity, 0.0f, 0.0f, 0.0f)
        }
    }

}
