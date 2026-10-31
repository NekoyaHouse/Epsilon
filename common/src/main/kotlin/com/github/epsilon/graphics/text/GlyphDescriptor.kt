package com.github.epsilon.graphics.text

import com.github.epsilon.graphics.text.ttf.TtfGlyphAtlas

data class GlyphDescriptor(
    val atlas: TtfGlyphAtlas,
    val uv: TtfGlyphAtlas.GlyphUV,
    val width: Int,
    val height: Int,
    val xOffset: Int,
    val yOffset: Int,
    val advance: Int
)
