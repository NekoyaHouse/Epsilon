package com.github.epsilon.graphics.text.ttf

import com.github.epsilon.Constants
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.LuminTexture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.CommandEncoder
import com.mojang.renderpearl.api.textures.AddressMode
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.OptionalDouble
import java.util.concurrent.atomic.AtomicInteger

class TtfGlyphAtlas(atlasId: Int) {

    private val texture: LuminTexture
    private val alphaTexture: LuminTexture
    private val textureId: Identifier
    private val alphaTextureId: Identifier

    private var currentX = 0
    private var currentY = 0
    private var currentRowHeight = 0

    init {
        textureId = Identifier.fromNamespaceAndPath("epsilon", "ttf_atlas/" + NEXT_TEXTURE_ID.getAndIncrement())
        alphaTextureId = Identifier.fromNamespaceAndPath("epsilon", "ttf_alpha_atlas/" + NEXT_TEXTURE_ID.getAndIncrement())

        val texture = RenderSystem.getDevice().createTexture(
            { "Lumin-TtfGlyphAtlas" },
            GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST,
            GpuFormat.R8_UNORM,
            SIZE, SIZE,
            1, 1
        )
        val alphaTexture = RenderSystem.getDevice().createTexture(
            { "Lumin-TtfGlyphAlphaAtlas" },
            GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST,
            GpuFormat.R8_UNORM,
            SIZE, SIZE,
            1, 1
        )

        val textureView = RenderSystem.getDevice().createTextureView(texture)
        val sampler = RenderSystem.getDevice().createSampler(
            AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
            FilterMode.LINEAR, FilterMode.LINEAR,
            1, OptionalDouble.empty()
        )

        this.texture = LuminTexture(texture, textureView, sampler)
        val alphaTextureView = RenderSystem.getDevice().createTextureView(alphaTexture)
        val alphaSampler = RenderSystem.getDevice().createSampler(
            AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
            FilterMode.LINEAR, FilterMode.LINEAR,
            1, OptionalDouble.empty()
        )
        this.alphaTexture = LuminTexture(alphaTexture, alphaTextureView, alphaSampler)
        fillTexture(texture, 0xFF.toByte())
        fillTexture(alphaTexture, 0x00.toByte())
        Constants.mc.getTextureManager().register(this.textureId, this.texture)
        Constants.mc.getTextureManager().register(this.alphaTextureId, this.alphaTexture)
    }

    /**
     * Try to append a glyph to atlas
     *
     * Return null if glyph atlas is full
     */
    fun appendGlyph(glyph: TtfGlyph): GlyphUV? {
        val glyphData = glyph.glyphData ?: return null

        val cellWidth = glyph.width + GLYPH_GUTTER * 2
        val cellHeight = glyph.height + GLYPH_GUTTER * 2

        if (currentX + cellWidth >= SIZE) {
            currentX = 0
            currentY += currentRowHeight
            currentRowHeight = 0
        }

        // Return null if glyph atlas is full
        if (currentY + cellHeight >= SIZE) {
            return null
        }

        val glyphX = currentX + GLYPH_GUTTER
        val glyphY = currentY + GLYPH_GUTTER

        uploadToTexture(
            this.texture.getTexture(),
            glyphData,
            glyphX, glyphY,
            glyph.width,
            glyph.height
        )
        val alphaData = glyph.alphaData
        if (alphaData != null) {
            uploadToTexture(
                this.alphaTexture.getTexture(),
                alphaData,
                glyphX, glyphY,
                glyph.width,
                glyph.height
            )
        }

        val uv = GlyphUV(
            glyphX.toFloat() / SIZE,
            glyphY.toFloat() / SIZE,
            (glyphX + glyph.width).toFloat() / SIZE,
            (glyphY + glyph.height).toFloat() / SIZE
        )

        currentX += cellWidth
        currentRowHeight = Math.max(currentRowHeight, cellHeight)

        return uv
    }

    fun getTexture(): LuminTexture {
        return texture
    }

    fun getAlphaTexture(): LuminTexture {
        return alphaTexture
    }

    fun getTextureId(): Identifier {
        return textureId
    }

    fun destroy() {
        Constants.mc.getTextureManager().release(this.textureId)
        Constants.mc.getTextureManager().release(this.alphaTextureId)
    }

    data class GlyphUV(val u0: Float, val v0: Float, val u1: Float, val v1: Float)

    companion object {

        private const val SIZE = 1024
        private const val GLYPH_GUTTER = 2
        /**
         * Vulkan 要求 bufferOffset 为格式 texel block size 的倍数，取 4 同时覆盖 R8（1）与 RGBA8（4）。
         */
        private const val STAGING_ALIGNMENT = 4L
        private val NEXT_TEXTURE_ID = AtomicInteger()

        fun getSize(): Int {
            return SIZE
        }

        private fun fillTexture(texture: GpuTexture, value: Byte) {
            val pixels = MemoryUtil.memAlloc(SIZE * SIZE)
            try {
                MemoryUtil.memSet(MemoryUtil.memAddress(pixels), value.toInt() and 0xFF, (SIZE * SIZE).toLong())
                uploadToTexture(texture, pixels, 0, 0, SIZE, SIZE)
            } finally {
                MemoryUtil.memFree(pixels)
            }
        }

        /**
         * 上传一段像素数据到 atlas 纹理。
         *
         * Vulkan 下 `VkBufferImageCopy.bufferOffset` 必须是目标格式 texel block size 的倍数
         * （VUID-vkCmdCopyBufferToImage-dstImage-07975），而 blaze3d 的
         * `writeToTexture(GpuTexture, ByteBuffer, ...)` 固定按 alignment = 1 申请 staging，
         * 且暂存区游标按原始长度前进。字形是 R8 且宽高不保证 4 字节对齐，一旦游标错位，
         * 之后走同一路径的 RGBA8 纹理上传（视频帧、动态纹理等）都会拿到非法的 bufferOffset。
         * 因此 Vulkan 下自行申请 4 字节对齐的暂存区，并把分配长度补齐到 4 的倍数；
         * 后端判定由 [LuminRenderSystem.IS_VULKAN_BACKEND] 提供。
         */
        private fun uploadToTexture(texture: GpuTexture, data: ByteBuffer, destX: Int, destY: Int, width: Int, height: Int) {
            val encoder: CommandEncoder = RenderSystem.getDevice().createCommandEncoder()
            if (!LuminRenderSystem.IS_VULKAN_BACKEND) {
                encoder.writeToTexture(texture, data, 0, 0, destX, destY, width, height)
                return
            }

            val stagingSize = Mth.roundToward(data.remaining().toLong(), STAGING_ALIGNMENT)
            encoder.transientMemory().allocateStaging(stagingSize, STAGING_ALIGNMENT, GpuBuffer.USAGE_COPY_SRC).use { staging ->
                MemoryUtil.memCopy(
                    MemoryUtil.memAddress(data),
                    MemoryUtil.memAddress(staging.data),
                    data.remaining().toLong()
                )
                encoder.copyBufferToTexture(
                    staging.slice,
                    0, 0,
                    width, height,
                    texture,
                    destX, destY,
                    width, height,
                    0, 0
                )
            }
        }
    }
}
