package com.github.epsilon.graphics

import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.renderer.texture.TextureHandle

open class LuminTexture(
    val texture: GpuTexture,
    val textureView: GpuTextureView,
    val sampler: GpuSampler,
    private val closeTexture: Boolean,
    private val closeSampler: Boolean,
) : TextureHandle, AutoCloseable {

    constructor(texture: GpuTexture, textureView: GpuTextureView, sampler: GpuSampler) :
            this(texture, textureView, sampler, true, true)

    override fun textureView(): GpuTextureView = textureView

    override fun sampler(): GpuSampler = sampler

    override fun close() {
        if (closeSampler) {
            sampler.close()
        }
        if (closeTexture) {
            textureView.close()
            texture.close()
        }
    }
}
