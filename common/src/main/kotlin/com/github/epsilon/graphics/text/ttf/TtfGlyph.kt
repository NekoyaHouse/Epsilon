package com.github.epsilon.graphics.text.ttf

import java.nio.ByteBuffer

data class TtfGlyph(
    val glyphData: ByteBuffer?,
    val alphaData: ByteBuffer?,
    val width: Int,
    val height: Int,
    val xOffset: Int,
    val yOffset: Int,
    val advance: Int
)
