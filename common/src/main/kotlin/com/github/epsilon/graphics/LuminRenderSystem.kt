package com.github.epsilon.graphics

import com.github.epsilon.Constants
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.shaders.FXAAShader
import com.github.epsilon.graphics.shaders.MotionBlurShader
import com.github.epsilon.graphics.shaders.OverlayAntiAlias
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.managers.RenderTargetManager
import com.github.epsilon.managers.RendererManager
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.utils.render.ScissorUtils
import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.pipeline.IndexType
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.textures.AddressMode
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.DynamicGpuDataStorage
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped
import net.minecraft.client.renderer.Projection
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import net.minecraft.client.renderer.rendertype.TextureTransform
import net.minecraft.client.renderer.state.WindowRenderState
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.Vector4f
import org.joml.Vector4fc
import java.util.OptionalDouble

object LuminRenderSystem {

    val guiOrthoProjection: Projection = Projection()

    private val guiProjectionMatrixBuffer: ProjectionMatrixBuffer = ProjectionMatrixBuffer("lumin-gui")

    /**
     * 当前 GPU 后端是否为 Vulkan。设备创建后后端不会再变，因此只判定一次。
     */
    val IS_VULKAN_BACKEND: Boolean = "Vulkan" == RenderSystem.getDevice().deviceInfo.backendName()

    private var activeTarget: LuminRenderTarget? = null
    private var renderFrameId = 0L

    fun setActiveTarget(target: LuminRenderTarget?) {
        activeTarget = target
    }

    fun destroyAll() {
        guiProjectionMatrixBuffer.close()
        FXAAShader.close()
        MotionBlurShader.close()
        OverlayAntiAlias.close()
        ShaderUniforms.closeAll()
        RenderTargetManager.INSTANCE.destroyAll()
        RendererManager.INSTANCE.destroyAll()
        StaticFontLoader.destroyDefault()
    }

    fun <T : DynamicGpuDataStorage.DynamicGpuData> writeDynamicUniform(
        key: String,
        label: String,
        uniformSize: Int,
        initialCapacity: Int,
        uniform: T,
    ): GpuBufferSlice {
        return ShaderUniforms.write(key, label, uniformSize, initialCapacity, uniform)
    }

    fun endDynamicUniformFrame() {
        ShaderUniforms.endFrame()
    }

    fun beginRenderFrame() {
        renderFrameId++
    }

    fun getRenderFrameId(): Long {
        return renderFrameId
    }

    fun getActiveTarget(): LuminRenderTarget? {
        return activeTarget
    }

    fun getGuiScale(): Double {
        return ClientSetting.getScale()
    }

    fun getScaledWidth(): Float {
        val windowState: WindowRenderState = Constants.mc.gameRenderer.gameRenderState().windowRenderState
        return (windowState.width / getGuiScale()).toFloat()
    }

    fun getScaledHeight(): Float {
        val windowState: WindowRenderState = Constants.mc.gameRenderer.gameRenderState().windowRenderState
        return (windowState.height / getGuiScale()).toFloat()
    }

    fun getScaledWidthInt(): Int {
        return Math.ceil(getScaledWidth().toDouble()).toInt()
    }

    fun getScaledHeightInt(): Int {
        return Math.ceil(getScaledHeight().toDouble()).toInt()
    }

    fun toEpsilonMouseX(mouseX: Double): Double {
        return mouseX * Constants.mc.window.guiScale / getGuiScale()
    }

    fun toEpsilonMouseY(mouseY: Double): Double {
        return mouseY * Constants.mc.window.guiScale / getGuiScale()
    }

    fun toMinecraftGuiX(epsilonX: Double): Double {
        return epsilonX * getGuiScale() / Constants.mc.window.guiScale
    }

    fun toMinecraftGuiY(epsilonY: Double): Double {
        return epsilonY * getGuiScale() / Constants.mc.window.guiScale
    }

    fun toEpsilonMouseX(mouseX: Int): Int {
        return Math.round(toEpsilonMouseX(mouseX.toDouble())).toInt()
    }

    fun toEpsilonMouseY(mouseY: Int): Int {
        return Math.round(toEpsilonMouseY(mouseY.toDouble())).toInt()
    }

    fun toEpsilonMouseEvent(event: MouseButtonEvent): MouseButtonEvent {
        return MouseButtonEvent(toEpsilonMouseX(event.x()), toEpsilonMouseY(event.y()), event.buttonInfo())
    }

    fun toFramebufferScissor(x: Float, y: Float, width: Float, height: Float): ScissorRect {
        return ScissorUtils.toFramebufferScissor(x, y, width, height)
    }

    fun toFramebufferScissor(x: Float, y: Float, width: Float, height: Float, guiHeight: Float): ScissorRect {
        return ScissorUtils.toFramebufferScissor(x, y, width, height, guiHeight)
    }

    fun toFramebufferScissor(x: Float, y: Float, width: Float, height: Float, guiHeight: Int): ScissorRect {
        return toFramebufferScissor(x, y, width, height, guiHeight.toFloat())
    }

    fun toFramebufferScissor(x: Float, y: Float, width: Float, height: Float, guiHeight: Double): ScissorRect {
        return toFramebufferScissor(x, y, width, height, guiHeight.toFloat())
    }

    fun applyOrthoProjection() {
        guiOrthoProjection
            .setupOrtho(
                -1000.0F, 1000.0F,
                getScaledWidth(),
                getScaledHeight(),
                true
            )
        RenderSystem.setProjectionMatrix(
            guiProjectionMatrixBuffer.getBuffer(guiOrthoProjection), ProjectionType.ORTHOGRAPHIC
        )
    }

    /**
     * 获取当前活动目标的 colorTextureView 和 depthTextureView。
     * 如果设置了 activeTarget，则使用 activeTarget；否则使用主 RenderTarget。
     */
    fun resolveColorView(): GpuTextureView? {
        val target = activeTarget
        if (target != null) return target.colorView()
        return Constants.mc.gameRenderer.mainRenderTarget().colorTextureView
    }

    fun resolveDepthView(): GpuTextureView? {
        val target = activeTarget
        if (target != null) return target.depthView()
        return Constants.mc.gameRenderer.mainRenderTarget().depthTextureView
    }

    fun prepareQuadRendering(vertexCount: Int): QuadRenderingInfo? {
        return prepareQuadRendering(vertexCount, true)
    }

    fun prepareQuadRendering(vertexCount: Int, applyProjection: Boolean): QuadRenderingInfo? {
        if (applyProjection) {
            applyOrthoProjection()
        }

        val colorView = resolveColorView()
        val depthView = resolveDepthView()
        if (colorView == null) return null

        val indexCount = vertexCount / 4 * 6
        val ibo = getQuadIndexBuffer(indexCount)

        val dynamicUniforms = writeTransform(
            RenderSystem.getModelViewMatrixCopy(),
            Vector4f(1f, 1f, 1f, 1f),
            Vector3f(0f, 0f, 0f),
            TextureTransform.DEFAULT_TEXTURING.createMatrix()
        )

        return QuadRenderingInfo(colorView, depthView, getQuadIndexType(), ibo, indexCount, dynamicUniforms)
    }

    fun getQuadIndexBuffer(indexCount: Int): GpuBuffer {
        val autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
        return autoIndices.getBuffer(indexCount)
    }

    fun getQuadIndexType(): IndexType {
        val autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
        return autoIndices.type()
    }

    fun writeTransform(
        modelView: Matrix4fc,
        colorModulator: Vector4fc,
        modelOffset: Vector3fc,
        textureMatrix: Matrix4fc,
    ): GpuBufferSlice {
        return RenderSystem.getDynamicUniforms().writeTransform(
            Matrix4f(modelView), Vector4f(colorModulator), Vector3f(modelOffset), Matrix4f(textureMatrix)
        )
    }

    fun writeDefaultGuiTransform(): GpuBufferSlice {
        return writeTransform(
            RenderSystem.getModelViewMatrixCopy(),
            Vector4f(1f, 1f, 1f, 1f),
            Vector3f(0f, 0f, 0f),
            TextureTransform.DEFAULT_TEXTURING.createMatrix()
        )
    }

    data class ScissorRect(val x: Int, val y: Int, val width: Int, val height: Int)

    data class QuadRenderingInfo(
        val colorView: GpuTextureView,
        val depthView: GpuTextureView?,
        val indexType: IndexType,
        val ibo: GpuBuffer,
        val indexCount: Int,
        val dynamicUniforms: GpuBufferSlice,
    )

    private object ShaderUniforms {

        private val UNIFORMS: MutableMap<String, DynamicGpuDataStorage<DynamicGpuDataStorage.DynamicGpuData>> =
            HashMap()

        @Suppress("UNCHECKED_CAST")
        fun <T : DynamicGpuDataStorage.DynamicGpuData> write(
            key: String,
            label: String,
            uniformSize: Int,
            initialCapacity: Int,
            uniform: T,
        ): GpuBufferSlice {
            val storage = UNIFORMS.computeIfAbsent(key) { ignored ->
                DynamicGpuDataStorageMapped<DynamicGpuDataStorage.DynamicGpuData>(
                    label, uniformSize, GpuBuffer.USAGE_UNIFORM, initialCapacity
                )
            } as DynamicGpuDataStorage<T>
            return storage.writeData(uniform)
        }

        fun endFrame() {
            for (storage in UNIFORMS.values) {
                storage.endFrame()
            }
        }

        fun closeAll() {
            for (storage in UNIFORMS.values) {
                storage.close()
            }
            UNIFORMS.clear()
        }
    }

    open class LuminRenderTarget private constructor(
        name: String,
        initialWidth: Int,
        initialHeight: Int,
        withDepth: Boolean,
    ) : AutoCloseable {

        private var colorTexture: LuminTexture? = null
        private var depthTexture: GpuTexture? = null
        private var depthView: GpuTextureView? = null
        private val identifier: Identifier = ResourceLocationUtils.getIdentifier("lumin-rt$name")
        private val useDepth: Boolean = withDepth
        private var width: Int = initialWidth
        private var height: Int = initialHeight
        private var closed: Boolean = false

        init {
            createTextures()
        }

        companion object {

            fun create(name: String, width: Int, height: Int): LuminRenderTarget {
                return RenderTargetManager.INSTANCE.register(LuminRenderTarget(name, width, height, false))
            }

            fun createWithDepth(name: String, width: Int, height: Int): LuminRenderTarget {
                return RenderTargetManager.INSTANCE.register(LuminRenderTarget(name, width, height, true))
            }
        }

        private fun createTextures() {
            closed = false
            val device = RenderSystem.getDevice()

            val colorTexture = device.createTexture(
                "lumin-rt-color",
                GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_COPY_SRC,
                GpuFormat.RGBA8_UNORM,
                width, height, 1, 1
            )
            val colorView = device.createTextureView(colorTexture)

            if (useDepth) {
                val depthTexture = device.createTexture(
                    "lumin-rt-depth",
                    GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_COPY_SRC,
                    GpuFormat.D32_FLOAT,
                    width, height, 1, 1
                )
                this.depthTexture = depthTexture
                depthView = device.createTextureView(depthTexture)
            }

            val sampler = RenderSystem.getDevice().createSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.NEAREST, FilterMode.NEAREST,
                1, OptionalDouble.empty()
            )

            this.colorTexture = LuminTexture(colorTexture, colorView, sampler)

            Constants.mc.textureManager.register(identifier, getColorTexture())
        }

        fun resize(newWidth: Int, newHeight: Int) {
            if (newWidth == width && newHeight == height) return
            destroyTextures()
            width = newWidth
            height = newHeight
            createTextures()
        }

        fun getIdentifier(): Identifier {
            return identifier
        }

        fun clear() {
            val encoder = RenderSystem.getDevice().createCommandEncoder()
            if (useDepth) {
                encoder.clearColorAndDepthTextures(
                    colorTexture!!.texture, Vector4f(0.0f, 0.0f, 0.0f, 0.0f), depthTexture!!, 0.0
                )
            } else {
                encoder.clearColorTexture(colorTexture!!.texture, Vector4f(0.0f, 0.0f, 0.0f, 0.0f))
            }
        }

        fun colorView(): GpuTextureView {
            return colorTexture!!.textureView
        }

        fun depthView(): GpuTextureView? {
            return depthView
        }

        fun colorTexture(): GpuTexture {
            return colorTexture!!.texture
        }

        fun sampler(): GpuSampler {
            return colorTexture!!.sampler
        }

        fun width(): Int {
            return width
        }

        fun height(): Int {
            return height
        }

        fun getColorTexture(): LuminTexture {
            return colorTexture!!
        }

        private fun destroyTextures() {
            if (closed) {
                return
            }
            closed = true
            Constants.mc.textureManager.release(identifier)
            depthView?.close()
            depthTexture?.close()
            colorTexture = null
            depthView = null
            depthTexture = null
        }

        override fun close() {
            destroyTextures()
            RenderTargetManager.INSTANCE.unregister(this)
        }
    }
}
