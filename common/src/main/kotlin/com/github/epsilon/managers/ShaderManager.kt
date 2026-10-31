package com.github.epsilon.managers

import com.github.epsilon.Constants
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.LuminBindGroupLayouts
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.modules.impl.render.Shaders
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.CommandEncoder
import com.mojang.renderpearl.api.pipeline.BlendFactor
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuSampler
import net.minecraft.client.renderer.DynamicGpuDataStorage
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeStorage
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import org.joml.Matrix4fStack
import org.joml.Matrix4fc
import org.joml.Vector4f
import java.awt.Color
import java.nio.ByteBuffer
import java.util.*

object ShaderManager {

    private val PARAMS_UNIFORMS_SIZE: Int = Std140SizeCalculator()
        .putVec2()
        .putVec2()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putVec2()
        .putFloat()
        .get()

    private val COLOR_UNIFORMS_SIZE: Int = Std140SizeCalculator()
        .putVec4()
        .putVec4()
        .putVec4()
        .putVec4()
        .putVec4()
        .putVec4()
        .get()

    private val GLOW_UNIFORMS_SIZE: Int = Std140SizeCalculator()
        .putVec4()
        .putVec4()
        .putVec4()
        .putVec4()
        .putVec4()
        .putVec4()
        .putVec4()
        .get()

    private var defaultPipeline: RenderPipeline? = null
    private var smokePipeline: RenderPipeline? = null
    private var gradientPipeline: RenderPipeline? = null
    private var snowPipeline: RenderPipeline? = null
    private var fadePipeline: RenderPipeline? = null
    private var glowPipeline: RenderPipeline? = null
    private var glowMaskPipeline: RenderPipeline? = null
    private var copyPipeline: RenderPipeline? = null
    private var shaderSwap: RenderTarget? = null
    private var glowSwap: RenderTarget? = null
    private var handTarget: RenderTarget? = null
    private var chestTarget: RenderTarget? = null

    /**
     * 模块自己的胸箱描边提交缓存。
     *
     * 26.3 的 `RenderSetup` 不再携带输出目标，描边提交必须自己准备 frame 并指定 RenderPass，
     * 因此单独收集后再渲染进 `chestTarget`。
     */
    private val chestOutlineStorage = SubmitNodeStorage()
    private val chestOutlineRenderTypes = Util.memoize<Identifier, RenderType> { texture ->
        RenderType.create(
            "epsilon_chest_outline",
            RenderSetup.builder(RenderPipelines.OUTLINE_NO_CULL)
                .withTexture("Sampler0", texture)
                .setOutline(RenderSetup.OutlineProperty.IS_OUTLINE)
                .createRenderSetup()
        )
    }
    private var renderingHands = false
    private var capturedHands = false
    private var capturedChests = false
    private var preparedChests = false

    fun processOutlineTarget(target: RenderTarget?, settings: Shaders.ShaderSettings) {
        if (target == null || target.width <= 0 || target.height <= 0 || target.colorTextureView == null) {
            return
        }

        ensureProgram()
        val useGlow = settings.outlineMode.`is`(Shaders.OutlineMode.Glow)
        if (useGlow) {
            processGlowTarget(target, settings)
            return
        }

        ensureSwap(target.width, target.height)
        val shader = settings.mode.value
        val shaderUniforms = writeShaderUniforms(
            target.width, target.height, settings, true, settings === Shaders.entityShader
        )
        renderPass("epsilon_shader_effect", target, shaderSwap, pipeline(shader), shaderUniforms, true)
        renderPass("epsilon_shader_copy", shaderSwap!!, target, copyPipeline, null, false)
    }

    /**
     * 处理实体描边目标并混回主目标。
     *
     * 26.3 的实体描边在 `LevelRenderer` 内部提交，Shaders 启用时会取消原版后处理链，
     * 因此需要在 LevelRenderer 渲染结束后自行处理描边目标并混回主目标。
     *
     * @param outlineTarget 实体描边目标
     */
    fun processEntityOutlineTarget(outlineTarget: RenderTarget?) {
        if (!Shaders.isEnabled || outlineTarget == null) {
            return
        }

        val mainTarget = Constants.client.gameRenderer.mainRenderTarget()
        if (mainTarget.colorTextureView == null) {
            return
        }

        processOutlineTarget(outlineTarget, Shaders.entityShader)
        outlineTarget.blitAndBlendToTexture(mainTarget.colorTextureView!!, mainTarget.depthTextureView!!)
    }

    fun beginHandOutlineCapture(width: Int, height: Int) {
        if (Shaders.hands) {
            ensureHandTarget(maxOf(1, width), maxOf(1, height))
            if (!capturedHands) {
                val encoder = RenderSystem.getDevice().createCommandEncoder()
                encoder.clearColorAndDepthTextures(
                    handTarget!!.colorTexture!!, Vector4f(0.0f, 0.0f, 0.0f, 0.0f),
                    handTarget!!.depthTexture!!, 1.0
                )
            }
            renderingHands = true
            capturedHands = true
        }
    }

    fun endHandOutlineCapture() {
        if (renderingHands) {
            renderingHands = false
        }
    }

    fun getHandOutlineTarget(): RenderTarget? {
        return if (renderingHands) handTarget else null
    }

    fun getChestOutlineTarget(): RenderTarget? {
        return chestTarget
    }

    fun prepareChestOutline(texture: Identifier): RenderType {
        if (!preparedChests) {
            val mainTarget = Constants.client.gameRenderer.mainRenderTarget()
            prepareChestOutlineTarget(mainTarget)
            preparedChests = true
        }
        capturedChests = true
        return chestOutlineRenderTypes.apply(texture)
    }

    /**
     * 获取胸箱描边的提交目标。
     *
     * @return 模块持有的提交缓存
     */
    fun chestOutlineCollector(): SubmitNodeCollector {
        return chestOutlineStorage
    }

    /**
     * 把已提交的手部描边渲染进 `handTarget`。
     *
     * @param frame 当前正在执行的手部渲染帧
     */
    fun renderHandOutline(frame: FeatureRenderDispatcher.PreparedFrame) {
        if (!capturedHands || handTarget == null || handTarget!!.colorTextureView == null) {
            return
        }
        renderOutlinePass("epsilon_hand_outline", handTarget!!, frame)
    }

    fun processHandOutlineTarget(mainTarget: RenderTarget?) {
        if (capturedHands) {
            capturedHands = false

            if (!Shaders.hands || handTarget == null || mainTarget == null || mainTarget.colorTextureView == null) {
                return
            }

            processOutlineTarget(handTarget, Shaders.handsShader)
            handTarget!!.blitAndBlendToTexture(mainTarget.colorTextureView!!, mainTarget.depthTextureView!!)
        }
    }

    fun prepareChestOutlineTarget(referenceTarget: RenderTarget?) {
        if (referenceTarget == null || referenceTarget.width <= 0 || referenceTarget.height <= 0) {
            return
        }

        ensureChestTarget(referenceTarget.width, referenceTarget.height)
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.clearColorAndDepthTextures(
            chestTarget!!.colorTexture!!, Vector4f(0.0f, 0.0f, 0.0f, 0.0f),
            chestTarget!!.depthTexture!!, 1.0
        )
    }

    fun processChestOutlineTarget(modelViewMatrix: Matrix4fc) {
        if (!capturedChests) {
            preparedChests = false
            return
        }

        val mainTarget = Constants.client.gameRenderer.mainRenderTarget()

        capturedChests = false
        preparedChests = false

        if (chestTarget == null || mainTarget.colorTextureView == null) {
            return
        }

        val modelViewStack: Matrix4fStack = RenderSystem.getModelViewStack()
        modelViewStack.pushMatrix()
        modelViewStack.mul(modelViewMatrix)

        Constants.client.gameRenderer.featureRenderDispatcher().prepareFrame(chestOutlineStorage).use { frame ->
            renderOutlinePass("epsilon_chest_outline", chestTarget!!, frame)
        }

        processOutlineTarget(chestTarget, Shaders.chestShader)
        chestTarget!!.blitAndBlendToTexture(mainTarget.colorTextureView!!, mainTarget.depthTextureView!!)

        modelViewStack.popMatrix()
    }

    private fun renderOutlinePass(name: String, target: RenderTarget, frame: FeatureRenderDispatcher.PreparedFrame) {
        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { name },
            target.colorTextureView!!,
            Optional.empty(),
            target.depthTextureView,
            OptionalDouble.empty()
        ).use { renderPass ->
            RenderSystem.bindDefaultUniforms(renderPass)
            frame.executeOutline(renderPass)
        }
    }

    private fun renderPass(
        name: String, input: RenderTarget, output: RenderTarget?, pipeline: RenderPipeline?,
        shaderUniforms: ShaderUniforms?, bindColors: Boolean,
    ) {
        if (pipeline == null || output == null) return
        if (input.colorTextureView == null || output.colorTextureView == null) {
            return
        }

        val encoder = RenderSystem.getDevice().createCommandEncoder()
        val sampler: GpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)

        encoder.createRenderPass(
            { name },
            output.colorTextureView!!,
            Optional.empty()
        ).use { renderPass ->
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline))
            RenderSystem.bindDefaultUniforms(renderPass)
            if (shaderUniforms != null) {
                renderPass.setUniform("ShaderParams", shaderUniforms.params!!)
                if (bindColors) {
                    renderPass.setUniform("ShaderColors", shaderUniforms.colors!!)
                }
            }
            renderPass.setUniform("InputSampler", input.colorTextureView!!, sampler)
            renderPass.draw(3, 1, 0, 0)
        }
    }

    private fun processGlowTarget(target: RenderTarget, settings: Shaders.ShaderSettings) {
        ensureSwap(target.width, target.height)
        ensureGlowSwap(target.width, target.height)

        if (shaderSwap!!.colorTexture == null || glowSwap!!.colorTexture == null) {
            return
        }

        val horizontalGlow = writeGlowUniforms(settings, target.width, target.height, 1.0f, 0.0f)
        val verticalGlow = writeGlowUniforms(settings, target.width, target.height, 0.0f, 1.0f)
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.copyTextureToTexture(
            target.colorTexture!!,
            shaderSwap!!.colorTexture!!,
            0, 0, 0, 0, 0,
            target.width, target.height
        )

        renderGlowPass(encoder, shaderSwap!!, glowSwap!!, horizontalGlow)
        renderGlowPass(encoder, glowSwap!!, target, verticalGlow)
        renderMaskPass(encoder, shaderSwap!!, target)
    }

    private fun renderGlowPass(
        encoder: CommandEncoder, input: RenderTarget, output: RenderTarget, glowUniforms: GpuBufferSlice?,
    ) {
        if (input.colorTextureView == null || output.colorTextureView == null) {
            return
        }

        val sampler: GpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)

        encoder.createRenderPass(
            { "epsilon_shader_glow" },
            output.colorTextureView!!,
            Optional.empty()
        ).use { renderPass ->
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(glowPipeline!!))
            RenderSystem.bindDefaultUniforms(renderPass)
            renderPass.setUniform("GlowConfig", glowUniforms!!)
            renderPass.setUniform("InputSampler", input.colorTextureView!!, sampler)
            renderPass.draw(3, 1, 0, 0)
        }
    }

    private fun renderMaskPass(encoder: CommandEncoder, input: RenderTarget, output: RenderTarget) {
        if (input.colorTextureView == null || output.colorTextureView == null) {
            return
        }

        encoder.createRenderPass(
            { "epsilon_shader_glow_mask" },
            output.colorTextureView!!,
            Optional.empty()
        ).use { renderPass ->
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(glowMaskPipeline!!))
            RenderSystem.bindDefaultUniforms(renderPass)
            renderPass.setUniform(
                "InputSampler", input.colorTextureView!!,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
            )
            renderPass.draw(3, 1, 0, 0)
        }
    }

    private fun writeGlowUniforms(
        settings: Shaders.ShaderSettings, targetWidth: Int, targetHeight: Int,
        directionX: Float, directionY: Float,
    ): GpuBufferSlice {
        val gradient = settings.glowColorMode.`is`(Shaders.GlowColorMode.Gradient)
        val color: Color = if (gradient) settings.glowColor1 else settings.glowColor
        return LuminRenderSystem.writeDynamicUniform(
            "shader_glow_config",
            "Epsilon Shader Glow Config UBO",
            GLOW_UNIFORMS_SIZE,
            8,
            GlowParams(
                targetWidth.toFloat(),
                targetHeight.toFloat(),
                Constants.client.window.guiScaledWidth.toFloat(),
                Constants.client.window.guiScaledHeight.toFloat(),
                color,
                settings.glowColor1,
                settings.glowColor2,
                settings.glowRadius.toFloat(),
                settings.glowExposure.toFloat(),
                if (gradient) settings.glowGradientSpeed.toFloat() else 0.0f,
                if (gradient) 1.0f else 0.0f,
                (Util.getMillis() % 100_000L) / 1000.0f,
                directionX,
                directionY,
                if (settings === Shaders.entityShader) 1.0f else 0.0f
            )
        )
    }

    private fun writeShaderUniforms(
        screenWidth: Int, screenHeight: Int, settings: Shaders.ShaderSettings,
        writeColors: Boolean, useTargetColors: Boolean,
    ): ShaderUniforms {
        val width = maxOf(1.0f, screenWidth.toFloat())
        val height = maxOf(1.0f, screenHeight.toFloat())
        val scaledWidth = maxOf(1.0f, Constants.client.window.guiScaledWidth.toFloat())
        val scaledHeight = maxOf(1.0f, Constants.client.window.guiScaledHeight.toFloat())
        val outline = settings.outlineColor
        val smokeOutline1 = settings.smokeOutlineColor1
        val smokeOutline2 = settings.smokeOutlineColor2
        val fill = settings.fillColor1
        val smokeFill1 = settings.fillColor2
        val smokeFill2 = settings.fillColor3

        return ShaderUniforms(
            LuminRenderSystem.writeDynamicUniform(
                "shader_params",
                "Epsilon Shader Params UBO",
                PARAMS_UNIFORMS_SIZE,
                8,
                ShaderParams(
                    width,
                    height,
                    settings.quality.toFloat(),
                    settings.lineWidth.toFloat(),
                    if (settings.glow) -1.0f else alpha(outline),
                    settings.fillAlpha / 255.0f,
                    settings.gradientAlpha / 255.0f,
                    (Util.getMillis() % 100_000L) / 1000.0f,
                    settings.factor.toFloat(),
                    settings.gradient.toFloat(),
                    settings.octaves.toFloat(),
                    scaledWidth,
                    scaledHeight,
                    if (useTargetColors) 1.0f else 0.0f
                )
            ),
            if (writeColors) LuminRenderSystem.writeDynamicUniform(
                "shader_colors",
                "Epsilon Shader Colors UBO",
                COLOR_UNIFORMS_SIZE,
                8,
                ShaderColors(outline, smokeOutline1, smokeOutline2, fill, smokeFill1, smokeFill2)
            ) else null
        )
    }

    private fun ensureProgram() {
        if (defaultPipeline == null) {
            defaultPipeline = pipeline("outline", true)
            smokePipeline = pipeline("smoke", true)
            gradientPipeline = pipeline("gradient", true)
            snowPipeline = pipeline("snow", true)
            fadePipeline = pipeline("fade", true)
            glowPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(ResourceLocationUtils.getIdentifier("pipelines/shader_glow"))
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(ResourceLocationUtils.getIdentifier("shader_glow"))
                .withBindGroupLayout(LuminBindGroupLayouts.GLOW_CONFIG)
                .withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
                .withCull(false)
                .build()
            glowMaskPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(ResourceLocationUtils.getIdentifier("pipelines/shader_glow_mask"))
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(ResourceLocationUtils.getIdentifier("shader_glow_mask"))
                .withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
                .withColorTargetState(
                    ColorTargetState(
                        BlendFunction(BlendFactor.ZERO, BlendFactor.ONE_MINUS_SRC_ALPHA)
                    )
                )
                .withCull(false)
                .build()
            copyPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(ResourceLocationUtils.getIdentifier("pipelines/shader_copy"))
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withVertexShader("core/screenquad")
                .withFragmentShader(ResourceLocationUtils.getIdentifier("shader_copy"))
                .withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
                .withCull(false)
                .build()
        }
    }

    private fun ensureSwap(width: Int, height: Int) {
        if (shaderSwap == null) {
            shaderSwap = TextureTarget("Epsilon Shader Swap", width, height, GpuFormat.RGBA8_UNORM, null)
        }

        if (shaderSwap!!.width != width || shaderSwap!!.height != height) {
            shaderSwap!!.resize(width, height)
        }
    }

    private fun ensureGlowSwap(width: Int, height: Int) {
        if (glowSwap == null) {
            glowSwap = TextureTarget("Epsilon Shader Glow Swap", width, height, GpuFormat.RGBA8_UNORM, null)
        }

        if (glowSwap!!.width != width || glowSwap!!.height != height) {
            glowSwap!!.resize(width, height)
        }
    }

    private fun ensureHandTarget(width: Int, height: Int) {
        if (handTarget == null) {
            handTarget = TextureTarget(
                "Epsilon Shader Hands", width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT
            )
        }

        if (handTarget!!.width != width || handTarget!!.height != height) {
            handTarget!!.resize(width, height)
        }
    }

    private fun ensureChestTarget(width: Int, height: Int) {
        if (chestTarget == null) {
            chestTarget = TextureTarget(
                "Epsilon Shader Chests", width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT
            )
        }

        if (chestTarget!!.width != width || chestTarget!!.height != height) {
            chestTarget!!.resize(width, height)
        }
    }

    private fun pipeline(shader: String, useColors: Boolean): RenderPipeline {
        val builder = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/shader_$shader"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("shader_$shader"))
            .withCull(false)

        builder.withBindGroupLayout(LuminBindGroupLayouts.SHADER_PARAMS)
        if (useColors) {
            builder.withBindGroupLayout(LuminBindGroupLayouts.SHADER_COLORS)
        }

        return builder.withBindGroupLayout(LuminBindGroupLayouts.INPUT_SAMPLER)
            .build()
    }

    private fun pipeline(shader: Shader): RenderPipeline {
        return when (shader) {
            Shader.Smoke -> smokePipeline!!
            Shader.Gradient -> gradientPipeline!!
            Shader.Snow -> snowPipeline!!
            Shader.Fade -> fadePipeline!!
            else -> defaultPipeline!!
        }
    }

    private fun red(color: Color): Float {
        return color.red / 255.0f
    }

    private fun green(color: Color): Float {
        return color.green / 255.0f
    }

    private fun blue(color: Color): Float {
        return color.blue / 255.0f
    }

    private fun alpha(color: Color): Float {
        return color.alpha / 255.0f
    }

    enum class Shader {
        Default,
        Smoke,
        Gradient,
        Snow,
        Fade
    }

    private data class ShaderUniforms(
        val params: GpuBufferSlice?,
        val colors: GpuBufferSlice?,
    )

    private data class ShaderParams(
        val width: Float,
        val height: Float,
        val quality: Float,
        val lineWidth: Float,
        val outlineAlpha: Float,
        val fillAlpha: Float,
        val gradientAlpha: Float,
        val time: Float,
        val gradientFactor: Float,
        val gradientScale: Float,
        val octaves: Float,
        val resolutionWidth: Float,
        val resolutionHeight: Float,
        val useTargetColors: Float,
    ) : DynamicGpuDataStorage.DynamicGpuData {
        override fun write(buffer: ByteBuffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(width, height)
                .putVec2(1.0f / width, 1.0f / height)
                .putFloat(quality)
                .putFloat(lineWidth)
                .putFloat(outlineAlpha)
                .putFloat(fillAlpha)
                .putFloat(gradientAlpha)
                .putFloat(time)
                .putFloat(gradientFactor)
                .putFloat(gradientScale)
                .putFloat(octaves)
                .putVec2(resolutionWidth, resolutionHeight)
                .putFloat(useTargetColors)
        }
    }

    private data class ShaderColors(
        val outline: Color,
        val smokeOutline1: Color,
        val smokeOutline2: Color,
        val fill: Color,
        val smokeFill1: Color,
        val smokeFill2: Color,
    ) : DynamicGpuDataStorage.DynamicGpuData {
        override fun write(buffer: ByteBuffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec4(red(outline), green(outline), blue(outline), alpha(outline))
                .putVec4(red(smokeOutline1), green(smokeOutline1), blue(smokeOutline1), alpha(smokeOutline1))
                .putVec4(red(smokeOutline2), green(smokeOutline2), blue(smokeOutline2), alpha(smokeOutline2))
                .putVec4(red(fill), green(fill), blue(fill), alpha(fill))
                .putVec4(red(smokeFill1), green(smokeFill1), blue(smokeFill1), alpha(smokeFill1))
                .putVec4(red(smokeFill2), green(smokeFill2), blue(smokeFill2), alpha(smokeFill2))
        }
    }

    private data class GlowParams(
        val targetWidth: Float,
        val targetHeight: Float,
        val resolutionWidth: Float,
        val resolutionHeight: Float,
        val color: Color,
        val color1: Color,
        val color2: Color,
        val radius: Float,
        val exposure: Float,
        val gradientSpeed: Float,
        val gradient: Float,
        val time: Float,
        val directionX: Float,
        val directionY: Float,
        val useTargetColors: Float,
    ) : DynamicGpuDataStorage.DynamicGpuData {
        override fun write(buffer: ByteBuffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec4(targetWidth, targetHeight, 0.0f, 0.0f)
                .putVec4(resolutionWidth, resolutionHeight, 0.0f, 0.0f)
                .putVec4(red(color), green(color), blue(color), alpha(color))
                .putVec4(red(color1), green(color1), blue(color1), alpha(color1))
                .putVec4(red(color2), green(color2), blue(color2), alpha(color2))
                .putVec4(radius, exposure, gradientSpeed, gradient)
                .putVec4(time, directionX, directionY, useTargetColors)
        }
    }

}