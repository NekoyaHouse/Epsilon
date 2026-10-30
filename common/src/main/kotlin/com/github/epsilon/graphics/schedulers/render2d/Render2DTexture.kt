package com.github.epsilon.graphics.schedulers.render2d

import com.github.epsilon.graphics.LuminTexture
import net.minecraft.resources.Identifier

/**
 * 2D 调度器中的纹理引用。
 *
 * Identifier 纹理由 TextureRenderer 在每次 flush 时从 Minecraft TextureManager 解析；
 * LuminTexture 用于玩家皮肤这类已经拿到 GPU 句柄的资源。
 */
sealed interface Render2DTexture {

    @JvmRecord
    data class IdentifierRef(val identifier: Identifier?, val linearFilter: Boolean) : Render2DTexture

    @JvmRecord
    data class LuminRef(val texture: LuminTexture?) : Render2DTexture

}
