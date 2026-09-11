package com.github.epsilon.graphics;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;

import java.util.Optional;

public class LuminRenderPipelines {

    private static final RenderPipeline.Snippet NO_BLEND_DEPTH_SNIPPET = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .buildSnippet();

    // 26.2 的 GUI 字体提交会直接使用 TextRenderable.guiPipeline()。
    // 自定义 TTF shader 也读取 dynamictransforms/projection，因此这里必须和原版 GUI_TEXT 一样声明
    // GLOBALS、MATRICES_PROJECTION、SAMPLER0 和 GUI 深度状态；否则 Vulkan 后端会按不完整的 bind group 渲染。
    private static final RenderPipeline.Snippet GUI_TTF_FONT_SNIPPET = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.empty())
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withShaderDefine("IS_GUI")
            .buildSnippet();

    public static final RenderPipeline RECTANGLE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/rectangle"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("rectangle"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("rectangle"))
            .withCull(false)
            .build();

    public static final RenderPipeline TTF_FONT_AA = textPipeline("pipelines/ttf_font_aa", "ttf_font_aa", DefaultVertexFormat.POSITION_TEX_COLOR, null);
    public static final RenderPipeline TTF_FONT_NO_AA = textPipeline("pipelines/ttf_font_no_aa", "ttf_font_no_aa", DefaultVertexFormat.POSITION_TEX_COLOR, null);
    public static final RenderPipeline TTF_FONT_BLUR = textPipeline("pipelines/ttf_font_blur", "ttf_font_blur", LuminVertexFormats.FONT_BLUR, LuminBindGroupLayouts.FONT_BLUR);
    public static final RenderPipeline TTF_FONT_GLITCH = textPipeline("pipelines/ttf_font_glitch", "ttf_font_glitch", LuminVertexFormats.FONT_BLUR, LuminBindGroupLayouts.GLITCH_DATA);

    public static final RenderPipeline ROUND_RECT = shapePipeline("pipelines/round_rectangle", "round_rectangle", LuminVertexFormats.ROUND_RECT);
    public static final RenderPipeline ROUND_RECT_OUTLINE = shapePipeline("pipelines/round_rectangle_outline", "round_rectangle_outline", LuminVertexFormats.ROUND_RECT_OUTLINE);
    public static final RenderPipeline SHADOW = shapePipeline("pipelines/shadow", "shadow", LuminVertexFormats.ROUND_RECT);
    public static final RenderPipeline ARC = shapePipeline("pipelines/arc", "arc", LuminVertexFormats.ARC);

    public static final RenderPipeline SEGMENTED_SHADOW = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/segmented_shadow"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("segmented_shadow"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("segmented_shadow"))
            .withBindGroupLayout(LuminBindGroupLayouts.SEGMENTED_SHADOW)
            .withCull(false)
            .build();

    public static final RenderPipeline TEXTURE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/texture"))
            .withVertexBinding(0, LuminVertexFormats.TEXTURE)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("texture"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("texture"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withCull(false)
            .build();

    public static final RenderPipeline TRIANGLE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/triangle"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withVertexShader(ResourceLocationUtils.getIdentifier("triangle"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("triangle"))
            .withCull(false)
            .build();

    private static RenderPipeline textPipeline(String location, String fragmentShader, VertexFormat format, BindGroupLayout customLayout) {
        RenderPipeline.Builder builder = RenderPipeline.builder(GUI_TTF_FONT_SNIPPET)
                .withLocation(ResourceLocationUtils.getIdentifier(location))
                .withVertexBinding(0, format)
                .withVertexShader(ResourceLocationUtils.getIdentifier(fragmentShader.equals("ttf_font_glitch") ? "ttf_font_glitch" : fragmentShader.equals("ttf_font_blur") ? "ttf_font_blur" : "ttf_font"))
                .withFragmentShader(ResourceLocationUtils.getIdentifier(fragmentShader))
                .withCull(false);
        if (customLayout != null) {
            builder.withBindGroupLayout(customLayout);
        }
        return builder.build();
    }

    private static RenderPipeline shapePipeline(String location, String shader, VertexFormat format) {
        return RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
                .withLocation(ResourceLocationUtils.getIdentifier(location))
                .withVertexBinding(0, format)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexShader(ResourceLocationUtils.getIdentifier(shader))
                .withFragmentShader(ResourceLocationUtils.getIdentifier(shader))
                .withCull(false)
                .build();
    }

}
