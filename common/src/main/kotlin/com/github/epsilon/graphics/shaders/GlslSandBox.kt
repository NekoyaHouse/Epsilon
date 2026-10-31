package com.github.epsilon.graphics.shaders

import com.github.epsilon.Constants
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.LuminBindGroupLayouts
import com.github.epsilon.graphics.LuminRenderSystem
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.renderer.DynamicGpuDataStorage
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import java.nio.ByteBuffer
import java.util.*

object GlslSandBox : AutoCloseable {

    val SEA_LEVEL: Identifier = ResourceLocationUtils.getIdentifier("menu/sea_level")

    val CLOUDS: Identifier = ResourceLocationUtils.getIdentifier("menu/clouds")

    val ALIEN_TERRAIN: Identifier = ResourceLocationUtils.getIdentifier("menu/alien_terrain")

    val INFERNO: Identifier = ResourceLocationUtils.getIdentifier("menu/inferno")

    val PLANET: Identifier = ResourceLocationUtils.getIdentifier("menu/planet")

    val BLACK_HOLE: Identifier = ResourceLocationUtils.getIdentifier("menu/black_hole")

    val MINECRAFT: Identifier = ResourceLocationUtils.getIdentifier("menu/minecraft")

    private val SANDBOX_INFO_SIZE: Int = Std140SizeCalculator()
        .putVec4()
        .putVec4()
        .get()

    private val pipelines: MutableMap<Identifier, RenderPipeline> = HashMap()

    private var initTime: Long = Util.getMillis()

    private fun getOrCreatePipeline(fragmentShader: Identifier): RenderPipeline {
        return pipelines.computeIfAbsent(fragmentShader) { shader ->
            RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(shader.namespace, "pipelines/glsl_sandbox/" + shader.path.replace('/', '_')))
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(shader)
                .withBindGroupLayout(LuminBindGroupLayouts.GLSL_SANDBOX_INFO)
                .withCull(false)
                .build()
        }
    }

    fun resetTime() {
        initTime = Util.getMillis()
    }

    fun render(fragmentShader: Identifier, mouseX: Double, mouseY: Double) {
        render(fragmentShader, mouseX, mouseY, initTime)
    }

    fun render(fragmentShader: Identifier, mouseX: Double, mouseY: Double, startTimeMs: Long) {
        val colorView: GpuTextureView = LuminRenderSystem.resolveColorView() ?: return

        val activeTarget = LuminRenderSystem.getActiveTarget()
        val targetWidth = if (activeTarget != null) activeTarget.width() else Constants.client.gameRenderer.mainRenderTarget().width
        val targetHeight = if (activeTarget != null) activeTarget.height() else Constants.client.gameRenderer.mainRenderTarget().height

        if (targetWidth <= 0 || targetHeight <= 0) return

        val scaleX = targetWidth / LuminRenderSystem.getScaledWidth()
        val scaleY = targetHeight / LuminRenderSystem.getScaledHeight()

        val mousePxX = mouseX.toFloat() * scaleX
        val mousePxY = mouseY.toFloat() * scaleY
        val mouseUvX = mousePxX / targetWidth
        val mouseUvY = (targetHeight - 1.0f - mousePxY) / targetHeight
        val elapsedTime = (Util.getMillis() - startTimeMs) / 1000.0f
        val sandboxInfo: GpuBufferSlice = LuminRenderSystem.writeDynamicUniform(
            "glsl_sandbox_info",
            "Lumin GLSL Sandbox UBO",
            SANDBOX_INFO_SIZE,
            4,
            SandboxInfo(targetWidth.toFloat(), targetHeight.toFloat(), elapsedTime, mouseUvX, mouseUvY, mousePxX, mousePxY)
        )

        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.createRenderPass(
            { "Lumin GLSL Sandbox" },
            colorView, Optional.empty(),
            LuminRenderSystem.resolveDepthView(), OptionalDouble.empty()
        ).use { pass ->
            pass.setPipeline(RenderSystem.getCompiledPipeline(getOrCreatePipeline(fragmentShader)))
            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform("GlslSandboxInfo", sandboxInfo)
            pass.draw(3, 1, 0, 0)
        }
    }

    override fun close() {
        pipelines.clear()
    }

    private data class SandboxInfo(
        val width: Float,
        val height: Float,
        val elapsedTime: Float,
        val mouseUvX: Float,
        val mouseUvY: Float,
        val mousePxX: Float,
        val mousePxY: Float
    ) : DynamicGpuDataStorage.DynamicGpuData {
        override fun write(buffer: ByteBuffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec4(width, height, elapsedTime, 0.0f)
                .putVec4(mouseUvX, mouseUvY, mousePxX, mousePxY)
        }
    }
}
