package com.github.epsilon.graphics

import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.renderer.texture.AbstractTexture

open class LuminTexture(
    texture: GpuTexture,
    textureView: GpuTextureView,
    sampler: GpuSampler,
    private val closeTexture: Boolean,
    private val closeSampler: Boolean,
) : AbstractTexture() {

    init {
        this.texture = texture
        this.textureView = textureView
        this.sampler = sampler
    }

    constructor(texture: GpuTexture, textureView: GpuTextureView, sampler: GpuSampler) :
        this(texture, textureView, sampler, true, true)

    override fun close() {
        if (closeSampler) {
            sampler.close()
        }
        if (closeTexture) {
            textureView!!.close()
            texture!!.close()
        }
    }
}
