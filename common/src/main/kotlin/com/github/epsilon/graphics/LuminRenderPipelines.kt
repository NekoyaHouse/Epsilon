package com.github.epsilon.graphics

import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.renderpearl.api.pipeline.*
import com.mojang.renderpearl.api.vertex.VertexFormat
import net.minecraft.client.renderer.BindGroupLayouts
import java.util.*

object LuminRenderPipelines {

    private val NO_BLEND_DEPTH_SNIPPET: RenderPipeline.Snippet = RenderPipeline.builder()
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .buildSnippet()

    // 26.3 的 GUI 字体提交会直接使用 TextRenderable.guiPipeline()。
    // 自定义 TTF shader 也读取 dynamictransforms/projection，因此这里必须和原版 GUI_TEXT 一样声明
    // GLOBALS、PROJECTION、DYNAMIC_TRANSFORMS、SAMPLER0 和 GUI 深度状态；否则 Vulkan 后端会按不完整的 bind group 渲染。
    private val GUI_TTF_FONT_SNIPPET: RenderPipeline.Snippet = RenderPipeline.builder()
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(Optional.empty<DepthStencilState>())
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withShaderDefine("IS_GUI")
        .buildSnippet()

    val RECTANGLE: RenderPipeline = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipelines/rectangle"))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withVertexShader(ResourceLocationUtils.getIdentifier("rectangle"))
        .withFragmentShader(ResourceLocationUtils.getIdentifier("rectangle"))
        .withCull(false)
        .build()

    val TTF_FONT_AA: RenderPipeline =
        textPipeline("pipelines/ttf_font_aa", "ttf_font_aa", DefaultVertexFormat.POSITION_TEX_COLOR, null)

    val TTF_FONT_NO_AA: RenderPipeline =
        textPipeline("pipelines/ttf_font_no_aa", "ttf_font_no_aa", DefaultVertexFormat.POSITION_TEX_COLOR, null)

    val TTF_FONT_BLUR: RenderPipeline =
        textPipeline("pipelines/ttf_font_blur", "ttf_font_blur", LuminVertexFormats.FONT_BLUR, LuminBindGroupLayouts.FONT_BLUR)

    val TTF_FONT_GLITCH: RenderPipeline =
        textPipeline("pipelines/ttf_font_glitch", "ttf_font_glitch", LuminVertexFormats.FONT_BLUR, LuminBindGroupLayouts.GLITCH_DATA)

    val ROUND_RECT: RenderPipeline =
        shapePipeline("pipelines/round_rectangle", "round_rectangle", LuminVertexFormats.ROUND_RECT)

    val ROUND_RECT_OUTLINE: RenderPipeline =
        shapePipeline("pipelines/round_rectangle_outline", "round_rectangle_outline", LuminVertexFormats.ROUND_RECT_OUTLINE)

    val SHADOW: RenderPipeline = shapePipeline("pipelines/shadow", "shadow", LuminVertexFormats.ROUND_RECT)

    val ARC: RenderPipeline = shapePipeline("pipelines/arc", "arc", LuminVertexFormats.ARC)

    val SEGMENTED_SHADOW: RenderPipeline = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipelines/segmented_shadow"))
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withVertexShader(ResourceLocationUtils.getIdentifier("segmented_shadow"))
        .withFragmentShader(ResourceLocationUtils.getIdentifier("segmented_shadow"))
        .withBindGroupLayout(LuminBindGroupLayouts.SEGMENTED_SHADOW)
        .withCull(false)
        .build()

    val TEXTURE: RenderPipeline = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipelines/texture"))
        .withVertexBinding(0, LuminVertexFormats.TEXTURE)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withVertexShader(ResourceLocationUtils.getIdentifier("texture"))
        .withFragmentShader(ResourceLocationUtils.getIdentifier("texture"))
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withCull(false)
        .build()

    val TRIANGLE: RenderPipeline = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipelines/triangle"))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .withVertexShader(ResourceLocationUtils.getIdentifier("triangle"))
        .withFragmentShader(ResourceLocationUtils.getIdentifier("triangle"))
        .withCull(false)
        .build()

    private fun textPipeline(
        location: String,
        fragmentShader: String,
        format: VertexFormat,
        customLayout: BindGroupLayout?,
    ): RenderPipeline {
        val builder = RenderPipeline.builder(GUI_TTF_FONT_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier(location))
            .withVertexBinding(0, format)
            .withVertexShader(
                ResourceLocationUtils.getIdentifier(
                    if (fragmentShader == "ttf_font_glitch") "ttf_font_glitch"
                    else if (fragmentShader == "ttf_font_blur") "ttf_font_blur"
                    else "ttf_font"
                )
            )
            .withFragmentShader(ResourceLocationUtils.getIdentifier(fragmentShader))
            .withCull(false)
        if (customLayout != null) {
            builder.withBindGroupLayout(customLayout)
        }
        return builder.build()
    }

    private fun shapePipeline(location: String, shader: String, format: VertexFormat): RenderPipeline =
        RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier(location))
            .withVertexBinding(0, format)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier(shader))
            .withFragmentShader(ResourceLocationUtils.getIdentifier(shader))
            .withCull(false)
            .build()
}
