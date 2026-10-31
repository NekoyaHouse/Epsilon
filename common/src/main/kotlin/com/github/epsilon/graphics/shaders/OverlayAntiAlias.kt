package com.github.epsilon.graphics.shaders

import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.LuminBindGroupLayouts
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.LuminRenderSystem.LuminRenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.commands.RenderPassDescriptor
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.renderer.RenderPipelines
import java.util.Optional

object OverlayAntiAlias {

    private const val MAX_TEXELS = 64L * 1024 * 1024
    private val RESOLVE_4: RenderPipeline = createResolvePipeline(2)
    private val RESOLVE_16: RenderPipeline = createResolvePipeline(4)
    private val DEPTH_4: RenderPipeline = createDepthPipeline(2)
    private val DEPTH_16: RenderPipeline = createDepthPipeline(4)

    private var overlay: LuminRenderTarget? = null

    internal fun chooseScale(width: Int, height: Int, requestedScale: Int, maxSize: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var scale = if (requestedScale >= 4) 4 else 2
        while (scale > 1 && (width.toLong() * scale > maxSize || height.toLong() * scale > maxSize || width.toLong() * height * scale * scale > MAX_TEXELS)) {
            scale /= 2
        }
        return scale
    }

    private fun createResolvePipeline(scale: Int): RenderPipeline {
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/overlay_aa_" + scale))
            .withVertexShader("core/screenquad")
            .withFragmentShader(ResourceLocationUtils.getIdentifier("overlay_aa"))
            .withShaderDefine("SSAA_SCALE", scale)
            .withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withCull(false)
            .build()
    }

    private fun createDepthPipeline(scale: Int): RenderPipeline {
        return RenderPipeline.builder()
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/overlay_aa_depth_" + scale))
            .withVertexShader("core/screenquad")
            .withFragmentShader(ResourceLocationUtils.getIdentifier("overlay_aa_depth"))
            .withShaderDefine("SSAA_SCALE", scale)
            .withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, true))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build()
    }

    fun render(requestedScale: Int, drawAction: Runnable) {
        RenderSystem.assertOnRenderThread()
        val previousTarget = LuminRenderSystem.getActiveTarget()
        val destination = LuminRenderSystem.resolveColorView()
        val sourceDepth = LuminRenderSystem.resolveDepthView()
        if (destination == null || sourceDepth == null || destination.texture().format != GpuFormat.RGBA8_UNORM) {
            drawAction.run()
            return
        }

        val width = destination.getWidth(0)
        val height = destination.getHeight(0)
        val maxSize = RenderSystem.getDevice().deviceInfo.limits().maxTextureSize()
        val scale = chooseScale(width, height, requestedScale, maxSize)
        if (scale == 1) {
            close()
            drawAction.run()
            return
        }

        val existingOverlay = overlay
        val currentOverlay: LuminRenderTarget = if (existingOverlay == null) {
            LuminRenderTarget.createWithDepth("-overlay-aa", width * scale, height * scale).also { overlay = it }
        } else {
            existingOverlay.resize(width * scale, height * scale)
            existingOverlay
        }
        currentOverlay.clear()
        resampleDepth(sourceDepth, scale)

        LuminRenderSystem.setActiveTarget(currentOverlay)
        try {
            drawAction.run()
        } finally {
            LuminRenderSystem.setActiveTarget(previousTarget)
        }

        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { "Epsilon Overlay AA Resolve" },
            destination,
            Optional.empty()
        ).use { pass ->
            pass.setPipeline(RenderSystem.getCompiledPipeline(if (scale == 4) RESOLVE_16 else RESOLVE_4))
            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform("InputSampler", currentOverlay.colorView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            pass.draw(3, 1, 0, 0)
        }
    }

    private fun resampleDepth(sourceDepth: GpuTextureView, scale: Int) {
        val currentOverlay = overlay!!
        val descriptor: RenderPassDescriptor = RenderPassDescriptor.builder { "Epsilon Overlay AA Depth" }
            .withDepthAttachment(currentOverlay.depthView()!!)
            .build()
        RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor).use { pass ->
            pass.setUniform("InputSampler", sourceDepth, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
            pass.setPipeline(RenderSystem.getCompiledPipeline(if (scale == 4) DEPTH_16 else DEPTH_4))
            pass.draw(3, 1, 0, 0)
        }
    }

    fun close() {
        val currentOverlay = overlay
        if (currentOverlay != null) {
            currentOverlay.close()
            overlay = null
        }
    }
}
