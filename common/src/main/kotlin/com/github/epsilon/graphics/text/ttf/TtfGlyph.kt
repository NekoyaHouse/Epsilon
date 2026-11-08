package com.github.epsilon.graphics.text.ttf

import java.nio.ByteBuffer

data class TtfGlyph(
    // RGBA8：RGB 保存内正外负的 MSDF，A 固定为 255；缓冲区统一由 MemoryUtil 分配和释放。
    val glyphData: ByteBuffer?,
    // 独立 R8 覆盖率供模糊、glitch 等特效使用，不能将 MSDF 的任一通道当作 alpha。
    val alphaData: ByteBuffer?,
    val width: Int,
    val height: Int,
    val xOffset: Int,
    val yOffset: Int,
    val advance: Int
)
