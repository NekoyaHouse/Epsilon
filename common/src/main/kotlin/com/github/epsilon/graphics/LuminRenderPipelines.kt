package com.github.epsilon.graphics

import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.renderpearl.api.pipeline.*
import net.minecraft.client.gui.GuiPipeline
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.pipeline.RenderPipelines
import java.util.*

object LuminRenderPipelines {

    // Vanilla builds these pipelines from render variants in 26.4. Lumin supplies the state
    // shared by its custom pipelines, whose topology and depth state can vary per effect.
    val POST_PROCESSING_SNIPPET: RenderPipeline.Snippet = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
        .withVertexShader("core/screentriangle")
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .buildSnippet()

    val DEBUG_FILLED_SNIPPET: RenderPipeline.Snippet = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withVertexShader("core/position_color")
        .withFragmentShader("core/position_color")
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withCull(false)
        .buildSnippet()

    val LINES_SNIPPET: RenderPipeline.Snippet = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.FOG)
        .withVertexShader("core/rendertype_lines")
        .withFragmentShader("core/rendertype_lines")
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
        .withPrimitiveTopology(PrimitiveTopology.LINES)
        .withDepthStencilState(DepthStencilState.DEFAULT)
        .buildSnippet()

    val GUI_TEXTURED_SNIPPET: RenderPipeline.Snippet = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withVertexShader("core/position_tex_color")
        .withFragmentShader("core/position_tex_color")
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .buildSnippet()

    private val NO_BLEND_DEPTH_SNIPPET: RenderPipeline.Snippet = RenderPipeline.builder()
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .buildSnippet()

    // 26.4 的 GUI 字体通过 GuiPipeline 取得实际管线。
    // 自定义 TTF shader 也读取 dynamictransforms/projection，因此这里必须和原版 GuiPipeline.TEXT 一样声明
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

    val TTF_FONT: RenderPipeline =
        textPipeline("pipelines/ttf_font", "ttf_font", DefaultVertexFormat.POSITION_TEX_COLOR, null)

    val GUI_TTF_FONT: GuiPipeline = GuiPipeline.of { TTF_FONT }

    val TTF_FONT_BLUR: RenderPipeline =
        textPipeline(
            "pipelines/ttf_font_blur",
            "ttf_font_blur",
            LuminVertexFormats.FONT_BLUR,
            LuminBindGroupLayouts.FONT_BLUR
        )

    val TTF_FONT_GLITCH: RenderPipeline =
        textPipeline(
            "pipelines/ttf_font_glitch",
            "ttf_font_glitch",
            LuminVertexFormats.FONT_BLUR,
            LuminBindGroupLayouts.GLITCH_DATA
        )

    val ROUND_RECT: RenderPipeline =
        shapePipeline("pipelines/round_rectangle", "round_rectangle", LuminVertexFormats.ROUND_RECT)

    val ROUND_RECT_OUTLINE: RenderPipeline =
        shapePipeline(
            "pipelines/round_rectangle_outline",
            "round_rectangle_outline",
            LuminVertexFormats.ROUND_RECT_OUTLINE
        )

    val UI_SHAPE: RenderPipeline =
        shapePipeline("pipelines/ui_shape", "ui_shape", LuminVertexFormats.ROUND_RECT_OUTLINE)

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

    /** 原版物品图集和 PIP 纹理保存预乘 alpha；淡出时 RGB 与 alpha 同步衰减。 */
    val ITEM_TEXTURE: RenderPipeline = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
        .withLocation(ResourceLocationUtils.getIdentifier("pipelines/item_texture"))
        .withVertexBinding(0, LuminVertexFormats.TEXTURE)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withVertexShader(ResourceLocationUtils.getIdentifier("texture"))
        .withFragmentShader(ResourceLocationUtils.getIdentifier("texture"))
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
        .withShaderDefine("EPSILON_PREMULTIPLIED_ALPHA")
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
