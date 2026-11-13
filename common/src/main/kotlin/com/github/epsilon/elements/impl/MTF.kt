package com.github.epsilon.elements.impl

import com.github.epsilon.elements.HudModule
import com.github.epsilon.graphics.text.SystemEmojiAtlas
import com.github.epsilon.gui.lib.RotatedTexture
import com.github.epsilon.gui.lib.UiScope
import net.minecraft.client.DeltaTracker
import java.awt.Color

object MTF : HudModule("MTF", 0f, 0f, 36f, 36f) {

    private val size by doubleSetting("Size", 32.0, 12.0..96.0, 1.0)
    private val speed by doubleSetting("Speed", 160.0, -720.0..720.0, 5.0)
    private val color by colorSetting("Color", Color(255, 255, 255, 255), true)

    override fun measure(deltaTracker: DeltaTracker) {
        val boxSize = size.toFloat()
        setBounds(boxSize, boxSize)
    }

    override fun UiScope.drawUi(deltaTracker: DeltaTracker) {
        val boxSize = width
        val rotation = (System.currentTimeMillis() % 3_600_000L) / 1000.0f * speed.toFloat()

        val fishcake = "\uD83C\uDF65"
        val glyph = SystemEmojiAtlas.get(fishcake)

        // 纹理铺满元素矩形，旋转中心是元素中心（相对坐标）
        RotatedTexture(
            glyph.texture, 0.0f, 0.0f, boxSize, boxSize,
            glyph.u0, glyph.v0, glyph.u1, glyph.v1,
            color, boxSize / 2.0f, boxSize / 2.0f, rotation
        )
    }

}
