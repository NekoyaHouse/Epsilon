package com.github.epsilon.graphics;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.AbstractTexture;

public class LuminTexture extends AbstractTexture {

    private final boolean closeTexture;
    private final boolean closeSampler;

    public LuminTexture(GpuTexture texture, GpuTextureView textureView, GpuSampler sampler, boolean closeTexture, boolean closeSampler) {
        this.texture = texture;
        this.textureView = textureView;
        this.sampler = sampler;
        this.closeTexture = closeTexture;
        this.closeSampler = closeSampler;
    }

    public LuminTexture(GpuTexture texture, GpuTextureView textureView, GpuSampler sampler) {
        this(texture, textureView, sampler, true, true);
    }

    @Override
    public void close() {
        if (closeSampler) {
            sampler.close();
        }
        if (closeTexture) {
            textureView.close();
            texture.close();
        }
    }

}
