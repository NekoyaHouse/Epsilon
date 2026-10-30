package com.github.epsilon.graphics.renderers

import com.github.epsilon.Constants
import com.github.epsilon.graphics.LuminRenderPipelines
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.LuminTexture
import com.github.epsilon.graphics.buffer.LuminRingBuffer
import com.github.epsilon.managers.RendererManager
import com.github.epsilon.utils.render.ScissorUtils
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import org.joml.Vector4fc
import org.lwjgl.system.MemoryUtil
import java.awt.Color
import java.util.LinkedHashMap
import java.util.Optional
import java.util.OptionalDouble

class TextureRenderer private constructor() : IRenderer {

    private val batches = LinkedHashMap<TextureKey, Batch>()

    private var scissorEnabled = false
    private var scissorX = 0
    private var scissorY = 0
    private var scissorW = 0
    private var scissorH = 0
    private var sharedDynamicUniforms: GpuBufferSlice? = null
    private var sharedMaxIndexCount = 0

    companion object {

        private const val STRIDE = 56
        private const val BUFFER_SIZE = 16 * 1024L
        private const val QUAD_BYTES = STRIDE * 4L

        fun create(): TextureRenderer = RendererManager.register(TextureRenderer())

    }

    fun addQuadTexture(
        texture: LuminTexture?, x: Float, y: Float, width: Float, height: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
    ) {
        addRoundedTexture(texture, x, y, width, height, 0f, u0, v0, u1, v1, color)
    }

    fun addQuadTexture(
        texture: Identifier?, x: Float, y: Float, width: Float, height: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
    ) {
        addRoundedTexture(texture, x, y, width, height, 0f, u0, v0, u1, v1, color, false)
    }

    fun addQuadTexture(
        texture: Identifier?, x: Float, y: Float, width: Float, height: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, color: Color, useLinearFilter: Boolean,
    ) {
        addRoundedTexture(texture, x, y, width, height, 0f, u0, v0, u1, v1, color, useLinearFilter)
    }

    fun addRoundedTexture(
        texture: Identifier?, x: Float, y: Float, width: Float, height: Float, radius: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
    ) {
        addRoundedTexture(texture, x, y, width, height, radius, u0, v0, u1, v1, color, false)
    }

    fun addRoundedTexture(
        texture: Identifier?, x: Float, y: Float, width: Float, height: Float, radius: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, color: Color, useLinearFilter: Boolean,
    ) {
        addRoundedTexture(
            texture as Any?, x, y, width, height, radius, radius, radius, radius,
            u0, v0, u1, v1, color, useLinearFilter
        )
    }

    fun addRoundedTexture(
        texture: LuminTexture?, x: Float, y: Float, width: Float, height: Float, radius: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
    ) {
        addRoundedTexture(
            texture as Any?, x, y, width, height, radius, radius, radius, radius,
            u0, v0, u1, v1, color, true
        )
    }

    fun addRoundedTexture(
        texture: Identifier?, x: Float, y: Float, width: Float, height: Float,
        radiusTL: Float, radiusTR: Float, radiusBR: Float, radiusBL: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, color: Color, useLinearFilter: Boolean,
    ) {
        addRoundedTexture(
            texture as Any?, x, y, width, height, radiusTL, radiusTR, radiusBR, radiusBL,
            u0, v0, u1, v1, color, useLinearFilter
        )
    }

    fun addRoundedTexture(
        texture: LuminTexture?, x: Float, y: Float, width: Float, height: Float,
        radiusTL: Float, radiusTR: Float, radiusBR: Float, radiusBL: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
    ) {
        addRoundedTexture(
            texture as Any?, x, y, width, height, radiusTL, radiusTR, radiusBR, radiusBL,
            u0, v0, u1, v1, color, true
        )
    }

    fun addRotatedTexture(
        texture: Identifier?, x: Float, y: Float, width: Float, height: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
        originX: Float, originY: Float, rotationDegrees: Float, useLinearFilter: Boolean,
    ) {
        addRotatedRoundedTexture(
            texture as Any?, x, y, width, height, 0.0f, 0.0f, 0.0f, 0.0f,
            u0, v0, u1, v1, color, originX, originY, rotationDegrees, useLinearFilter
        )
    }

    fun addRotatedTexture(
        texture: LuminTexture?, x: Float, y: Float, width: Float, height: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
        originX: Float, originY: Float, rotationDegrees: Float,
    ) {
        addRotatedRoundedTexture(
            texture, x, y, width, height, 0.0f, 0.0f, 0.0f, 0.0f,
            u0, v0, u1, v1, color, originX, originY, rotationDegrees
        )
    }

    fun addRotatedRoundedTexture(
        texture: Identifier?, x: Float, y: Float, width: Float, height: Float,
        radiusTopLeft: Float, radiusTopRight: Float,
        radiusBottomRight: Float, radiusBottomLeft: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
        originX: Float, originY: Float, rotationDegrees: Float, useLinearFilter: Boolean,
    ) {
        addRotatedRoundedTexture(
            texture as Any?, x, y, width, height,
            radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
            u0, v0, u1, v1, color, originX, originY, rotationDegrees, useLinearFilter
        )
    }

    fun addRotatedRoundedTexture(
        texture: LuminTexture?, x: Float, y: Float, width: Float, height: Float,
        radiusTopLeft: Float, radiusTopRight: Float,
        radiusBottomRight: Float, radiusBottomLeft: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
        originX: Float, originY: Float, rotationDegrees: Float,
    ) {
        addRotatedRoundedTexture(
            texture as Any?, x, y, width, height,
            radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
            u0, v0, u1, v1, color, originX, originY, rotationDegrees, true
        )
    }

    private fun addRoundedTexture(
        textureKey: Any?, x: Float, y: Float, width: Float, height: Float,
        rTL: Float, rTR: Float, rBR: Float, rBL: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, color: Color, useLinearFilter: Boolean,
    ) {
        val batch = batches.getOrPut(TextureKey(textureKey, useLinearFilter)) {
            Batch(LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX))
        }

        batch.buffer.ensureCapacity(batch.currentOffset + QUAD_BYTES)
        batch.buffer.tryMap()

        val argb = ARGB.toABGR(color.getRGB())

        val x2 = x + width
        val y2 = y + height

        val baseAddr = MemoryUtil.memAddress(batch.buffer.getMappedBuffer())
        val p = baseAddr + batch.currentOffset

        writeVertex(p, x, y, u0, v0, argb, 0.0f, 0.0f, width, height, rTL, rTR, rBR, rBL)
        writeVertex(p + STRIDE, x, y2, u0, v1, argb, 0.0f, height, width, height, rTL, rTR, rBR, rBL)
        writeVertex(p + STRIDE * 2L, x2, y2, u1, v1, argb, width, height, width, height, rTL, rTR, rBR, rBL)
        writeVertex(p + STRIDE * 3L, x2, y, u1, v0, argb, width, 0.0f, width, height, rTL, rTR, rBR, rBL)

        batch.currentOffset += QUAD_BYTES
        batch.vertexCount += 4
    }

    private fun addRotatedRoundedTexture(
        textureKey: Any?, x: Float, y: Float, width: Float, height: Float,
        rTL: Float, rTR: Float, rBR: Float, rBL: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
        originX: Float, originY: Float, rotationDegrees: Float, useLinearFilter: Boolean,
    ) {
        val batch = batches.getOrPut(TextureKey(textureKey, useLinearFilter)) {
            Batch(LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX))
        }

        batch.buffer.ensureCapacity(batch.currentOffset + QUAD_BYTES)
        batch.buffer.tryMap()

        val argb = ARGB.toABGR(color.getRGB())
        val x2 = x + width
        val y2 = y + height
        val radians = Math.toRadians(rotationDegrees.toDouble()).toFloat()
        val cos = Math.cos(radians.toDouble()).toFloat()
        val sin = Math.sin(radians.toDouble()).toFloat()

        val rx1 = rotateX(x, y, originX, originY, cos, sin)
        val ry1 = rotateY(x, y, originX, originY, cos, sin)
        val rx2 = rotateX(x, y2, originX, originY, cos, sin)
        val ry2 = rotateY(x, y2, originX, originY, cos, sin)
        val rx3 = rotateX(x2, y2, originX, originY, cos, sin)
        val ry3 = rotateY(x2, y2, originX, originY, cos, sin)
        val rx4 = rotateX(x2, y, originX, originY, cos, sin)
        val ry4 = rotateY(x2, y, originX, originY, cos, sin)
        val baseAddr = MemoryUtil.memAddress(batch.buffer.getMappedBuffer())
        val p = baseAddr + batch.currentOffset

        writeVertex(p, rx1, ry1, u0, v0, argb, 0.0f, 0.0f, width, height, rTL, rTR, rBR, rBL)
        writeVertex(p + STRIDE, rx2, ry2, u0, v1, argb, 0.0f, height, width, height, rTL, rTR, rBR, rBL)
        writeVertex(p + STRIDE * 2L, rx3, ry3, u1, v1, argb, width, height, width, height, rTL, rTR, rBR, rBL)
        writeVertex(p + STRIDE * 3L, rx4, ry4, u1, v0, argb, width, 0.0f, width, height, rTL, rTR, rBR, rBL)

        batch.currentOffset += QUAD_BYTES
        batch.vertexCount += 4
    }

    private fun rotateX(x: Float, y: Float, originX: Float, originY: Float, cos: Float, sin: Float): Float {
        val dx = x - originX
        val dy = y - originY
        return originX + dx * cos - dy * sin
    }

    private fun rotateY(x: Float, y: Float, originX: Float, originY: Float, cos: Float, sin: Float): Float {
        val dx = x - originX
        val dy = y - originY
        return originY + dx * sin + dy * cos
    }

    private fun writeVertex(
        addr: Long, x: Float, y: Float, u: Float, v: Float, color: Int,
        localX: Float, localY: Float, localWidth: Float, localHeight: Float,
        r1: Float, r2: Float, r3: Float, r4: Float,
    ) {
        MemoryUtil.memPutFloat(addr, x)
        MemoryUtil.memPutFloat(addr + 4, y)
        MemoryUtil.memPutFloat(addr + 8, 0.0f) // z
        MemoryUtil.memPutInt(addr + 12, color)
        MemoryUtil.memPutFloat(addr + 16, u)
        MemoryUtil.memPutFloat(addr + 20, v)
        MemoryUtil.memPutFloat(addr + 24, localX)
        MemoryUtil.memPutFloat(addr + 28, localY)
        MemoryUtil.memPutFloat(addr + 32, localWidth)
        MemoryUtil.memPutFloat(addr + 36, localHeight)
        // Radius vector (TL, TR, BR, BL)
        MemoryUtil.memPutFloat(addr + 40, r1)
        MemoryUtil.memPutFloat(addr + 44, r2)
        MemoryUtil.memPutFloat(addr + 48, r3)
        MemoryUtil.memPutFloat(addr + 52, r4)
    }

    fun setScissor(x: Int, y: Int, width: Int, height: Int) {
        val scissor = ScissorUtils.clampFramebufferScissor(x, y, width, height)
        scissorEnabled = true
        scissorX = scissor.x
        scissorY = scissor.y
        scissorW = scissor.width
        scissorH = scissor.height
    }

    fun clearScissor() {
        scissorEnabled = false
    }

    override fun draw() {
        if (batches.isEmpty()) return

        LuminRenderSystem.applyOrthoProjection()

        val colorView = LuminRenderSystem.resolveColorView() ?: return
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return

        val maxIndexCount = prepareTextureBatches()
        if (maxIndexCount == 0) return

        val dynamicUniforms = LuminRenderSystem.writeDefaultGuiTransform()
        val ibo = LuminRenderSystem.getQuadIndexBuffer(maxIndexCount)
        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { "Rounded Texture Draws" },
            colorView, Optional.empty<Vector4fc>(),
            null, OptionalDouble.empty()
        ).use { pass ->
            pass.setPipeline(RenderSystem.getCompiledPipeline(LuminRenderPipelines.TEXTURE))
            if (scissorEnabled) {
                ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)
            }

            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform("DynamicTransforms", dynamicUniforms)
            pass.setIndexBuffer(ibo, LuminRenderSystem.getQuadIndexType())

            drawPrepared(pass)
        }
    }

    override fun prepareSharedDraw(): Boolean {
        sharedDynamicUniforms = null
        sharedMaxIndexCount = 0
        if (batches.isEmpty()) return false
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return false

        sharedMaxIndexCount = prepareTextureBatches()
        if (sharedMaxIndexCount == 0) return false

        LuminRenderSystem.getQuadIndexBuffer(sharedMaxIndexCount)
        sharedDynamicUniforms = LuminRenderSystem.writeDefaultGuiTransform()
        return sharedDynamicUniforms != null
    }

    override fun draw(pass: RenderPass) {
        if (sharedDynamicUniforms == null || sharedMaxIndexCount == 0) return

        pass.setIndexBuffer(LuminRenderSystem.getQuadIndexBuffer(sharedMaxIndexCount), LuminRenderSystem.getQuadIndexType())
        pass.setUniform("DynamicTransforms", sharedDynamicUniforms!!)
        drawPrepared(pass)
    }

    private fun prepareTextureBatches(): Int {
        var maxIndexCount = 0
        for ((key, batch) in batches.entries) {
            batch.preparedTexture = null
            if (batch.vertexCount == 0) continue

            if (batch.buffer.isMapped()) {
                batch.buffer.unmap()
            }

            batch.preparedTexture = resolveTexture(key)
            if (batch.preparedTexture == null) continue
            maxIndexCount = Math.max(maxIndexCount, batch.vertexCount / 4 * 6)
        }
        return maxIndexCount
    }

    private fun resolveTexture(key: TextureKey): PreparedTexture? {
        val texture = key.texture
        if (texture is Identifier) {
            val abstractTexture = Constants.mc.getTextureManager().getTexture(texture)
            val sampler = if (key.linearFilter) {
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            } else {
                abstractTexture.getSampler()
            }
            return PreparedTexture(abstractTexture.getTextureView(), sampler)
        }
        if (texture is LuminTexture) {
            return PreparedTexture(texture.getTextureView(), texture.getSampler())
        }
        return null
    }

    private fun drawPrepared(pass: RenderPass) {
        if (scissorEnabled) {
            if (!ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)) {
                return
            }
        } else {
            pass.disableScissor()
        }

        // 纹理解析和上传已经在 prepare 阶段完成，pass 内只允许绑定和 draw。
        for (batch in batches.values) {
            if (batch.vertexCount == 0 || batch.preparedTexture == null) continue

            val indexCount = batch.vertexCount / 4 * 6
            val texture = batch.preparedTexture ?: continue

            pass.setVertexBuffer(0, batch.buffer.getGpuBuffer().slice())
            pass.setUniform("Sampler0", texture.view, texture.sampler)
            pass.drawIndexed(indexCount, 1, 0, 0, 0)
        }
    }

    override fun clear() {
        for (batch in batches.values) {
            if (batch.vertexCount > 0) {
                if (batch.buffer.isMapped()) {
                    batch.buffer.unmap()
                }
                batch.buffer.rotate()
            }
            batch.currentOffset = 0L
            batch.vertexCount = 0
            batch.preparedTexture = null
        }
        sharedDynamicUniforms = null
        sharedMaxIndexCount = 0
    }

    override fun close() {
        clear()
        for (batch in batches.values) {
            batch.buffer.close()
        }
        batches.clear()
        RendererManager.unregister(this)
    }

    private data class TextureKey(val texture: Any?, val linearFilter: Boolean)

    private data class PreparedTexture(val view: GpuTextureView, val sampler: GpuSampler)

    private class Batch(val buffer: LuminRingBuffer) {
        var currentOffset = 0L
        var vertexCount = 0
        var preparedTexture: PreparedTexture? = null
    }

}
