package com.github.epsilon.graphics

import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.vertex.VertexFormat

class LuminVertexFormats private constructor() {

    companion object {

        @JvmField
        val ROUND_RECT: VertexFormat = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("InnerRect", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Radius", GpuFormat.RGBA32_FLOAT)
            .build()

        @JvmField
        val ROUND_RECT_OUTLINE: VertexFormat = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("InnerRect", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Radius", GpuFormat.RGBA32_FLOAT)
            .addAttribute("OutlineWidth", GpuFormat.R32_FLOAT)
            .build()

        @JvmField
        val ARC: VertexFormat = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("StartColor", GpuFormat.RGBA8_UNORM)
            .addAttribute("MiddleColor", GpuFormat.RGBA8_UNORM)
            .addAttribute("EndColor", GpuFormat.RGBA8_UNORM)
            .addAttribute("Circle", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Sweep", GpuFormat.RGBA32_FLOAT)
            .build()

        @JvmField
        val TEXTURE: VertexFormat = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("InnerRect", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Radius", GpuFormat.RGBA32_FLOAT)
            .build()

        @JvmField
        val FONT_BLUR: VertexFormat = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("GlyphUvBounds", GpuFormat.RGBA32_FLOAT)
            .build()
    }
}
