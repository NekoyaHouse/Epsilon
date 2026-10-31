package com.github.epsilon.graphics

import com.github.epsilon.Constants
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.utils.network.Http
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import java.awt.image.BufferedImage
import java.io.IOException
import javax.imageio.ImageIO

open class PlayerHeadTexture private constructor(uploaded: Uploaded) :
    LuminTexture(uploaded.texture, uploaded.view, uploaded.sampler, true, false) {

    constructor(head: ByteArray) : this(upload(toNativeImage(head)))

    constructor() : this(upload(loadSteveHead()))

    private data class Uploaded(val texture: GpuTexture, val view: GpuTextureView, val sampler: GpuSampler)

    companion object {

        private fun toNativeImage(head: ByteArray): NativeImage {
            val image = NativeImage(8, 8, false)
            image.pixelBytes.put(head).rewind()
            return image
        }

        private fun loadSteveHead(): NativeImage {
            return try {
                Constants.client.resourceManager
                    .getResourceOrThrow(ResourceLocationUtils.getIdentifier("textures/gui/steve.png"))
                    .open()
                    .use { stream -> NativeImage.read(stream) }
            } catch (e: IOException) {
                MissingTextureAtlasSprite.generateMissingImage()
            }
        }

        private fun upload(image: NativeImage): Uploaded {
            val device = RenderSystem.getDevice()
            val texture: GpuTexture = device.createTexture(
                "player-head",
                GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.RGBA8_UNORM,
                image.width, image.height, 1, 1
            )
            device.createCommandEncoder().writeToTexture(texture, image)
            val view: GpuTextureView = device.createTextureView(texture)
            val sampler: GpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
            image.close()
            return Uploaded(texture, view, sampler)
        }

        fun downloadHead(url: String): ByteArray {
            val skin: BufferedImage? = Http.get(url).sendInputStream().use { input ->
                ImageIO.read(input)
            }

            if (skin == null) throw IOException("Failed to decode skin image.")

            val head = ByteArray(8 * 8 * 4)
            val pixel = IntArray(4)

            var i = 0
            for (x in 8 until 16) {
                for (y in 8 until 16) {
                    skin.data.getPixel(x, y, pixel)

                    for (j in 0 until 4) {
                        head[i++] = pixel[j].toByte()
                    }
                }
            }

            i = 0
            for (x in 40 until 48) {
                for (y in 8 until 16) {
                    skin.data.getPixel(x, y, pixel)

                    if (pixel[3] != 0) {
                        for (j in 0 until 4) {
                            head[i++] = pixel[j].toByte()
                        }
                    } else {
                        i += 4
                    }
                }
            }

            // 顺时针旋转 90°：源像素 (x, y) -> 目标 (7 - y, x)
            val rotated = ByteArray(8 * 8 * 4)
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    System.arraycopy(head, (y * 8 + x) * 4, rotated, (x * 8 + (7 - y)) * 4, 4)
                }
            }

            return rotated
        }
    }
}
