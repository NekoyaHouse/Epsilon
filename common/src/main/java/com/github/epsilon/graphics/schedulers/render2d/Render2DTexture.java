package com.github.epsilon.graphics.schedulers.render2d;

import com.github.epsilon.graphics.LuminTexture;
import net.minecraft.resources.Identifier;

/**
 * 2D 调度器中的纹理引用。
 * <p>
 * Identifier 纹理由 TextureRenderer 在每次 flush 时从 Minecraft TextureManager 解析；
 * LuminTexture 用于玩家皮肤这类已经拿到 GPU 句柄的资源。
 */
public sealed interface Render2DTexture permits Render2DTexture.IdentifierRef, Render2DTexture.LuminRef {

    record IdentifierRef(Identifier identifier, boolean linearFilter) implements Render2DTexture {
    }

    record LuminRef(LuminTexture texture) implements Render2DTexture {
    }
}
