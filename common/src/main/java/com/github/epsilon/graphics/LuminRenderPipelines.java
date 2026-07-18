package com.github.epsilon.graphics;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;


public class LuminRenderPipelines {

    private final static RenderPipeline.Snippet NO_BLEND_DEPTH_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .buildSnippet();

    public final static RenderPipeline RECTANGLE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/rectangle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("rectangle"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("rectangle"))
            .withCull(false)
            .build();

    public final static RenderPipeline TTF_FONT_AA = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/ttf_font_aa"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("ttf_font"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("ttf_font_aa"))
            .withSampler("Sampler0")
            .withCull(false)
            .build();

    public final static RenderPipeline TTF_FONT_NO_AA = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/ttf_font_no_aa"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("ttf_font"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("ttf_font_no_aa"))
            .withSampler("Sampler0")
            .withCull(false)
            .build();

    public final static RenderPipeline ROUND_RECT = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/round_rectangle"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("round_rectangle"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("round_rectangle"))
            .withCull(false)
            .build();

    public final static RenderPipeline ROUND_RECT_OUTLINE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/round_rectangle_outline"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT_OUTLINE, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("round_rectangle_outline"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("round_rectangle_outline"))
            .withCull(false)
            .build();

    public final static RenderPipeline SHADOW = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/shadow"))
            .withVertexFormat(LuminVertexFormats.ROUND_RECT, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("shadow"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("shadow"))
            .withCull(false)
            .build();

    public final static RenderPipeline TEXTURE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/texture"))
            .withVertexFormat(LuminVertexFormats.TEXTURE, VertexFormat.Mode.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("texture"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("texture"))
            .withSampler("Sampler0")
            .withCull(false)
            .build();

    public final static RenderPipeline TRIANGLE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/triangle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withVertexShader(ResourceLocationUtils.getIdentifier("triangle"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("triangle"))
            .withCull(false)
            .build();

}
