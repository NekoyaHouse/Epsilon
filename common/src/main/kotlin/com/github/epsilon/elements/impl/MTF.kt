package com.github.epsilon.elements.impl

import com.github.epsilon.elements.HudModule
import com.github.epsilon.graphics.text.SystemEmojiAtlas
import net.minecraft.client.DeltaTracker
import java.awt.Color

object MTF : HudModule("MTF", 0f, 0f, 36f, 36f) {

    private val size by doubleSetting("Size", 32.0, 12.0, 96.0, 1.0)
    private val speed by doubleSetting("Speed", 160.0, -720.0, 720.0, 5.0)
    private val color by colorSetting("Color", Color(255, 255, 255, 255), true)

    override fun render(deltaTracker: DeltaTracker) {
        val boxSize = size.toFloat()
        setBounds(boxSize, boxSize)

        val originX = x + boxSize / 2.0f
        val originY = y + boxSize / 2.0f
        val rotation = (System.currentTimeMillis() % 3_600_000L) / 1000.0f * speed.toFloat()

        val fishcake = "\uD83C\uDF65"
        val glyph = SystemEmojiAtlas.get(fishcake)

        renderScope().rotatedTexture(
            glyph.texture, x, y, boxSize, boxSize,
            glyph.u0, glyph.v0, glyph.u1, glyph.v1,
            color, originX, originY, rotation
        )
    }

}
