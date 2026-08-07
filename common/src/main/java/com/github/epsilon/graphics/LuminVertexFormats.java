package com.github.epsilon.graphics;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

public class LuminVertexFormats {

    public static final VertexFormat ROUND_RECT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("InnerRect", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Radius", GpuFormat.RGBA32_FLOAT)
            .build();

    public static final VertexFormat ROUND_RECT_OUTLINE = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("InnerRect", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Radius", GpuFormat.RGBA32_FLOAT)
            .addAttribute("OutlineWidth", GpuFormat.R32_FLOAT)
            .build();

    public static final VertexFormat ARC = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("StartColor", GpuFormat.RGBA8_UNORM)
            .addAttribute("MiddleColor", GpuFormat.RGBA8_UNORM)
            .addAttribute("EndColor", GpuFormat.RGBA8_UNORM)
            .addAttribute("Circle", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Sweep", GpuFormat.RGBA32_FLOAT)
            .build();

    public static final VertexFormat TEXTURE = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("InnerRect", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Radius", GpuFormat.RGBA32_FLOAT)
            .build();

    public static final VertexFormat FONT_BLUR = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("GlyphUvBounds", GpuFormat.RGBA32_FLOAT)
            .build();

    private LuminVertexFormats() {
    }
}
