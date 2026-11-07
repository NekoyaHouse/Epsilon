package com.github.epsilon.graphics.schedulers.render2d

import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.LuminRenderPipelines
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.ShaderType
import com.mojang.renderpearl.api.vertex.VertexFormat
import java.util.Optional

/** 每实例属性让不同组件的变换与 tint 共享一次绑定。 */
internal object Render2DPipelines {
    val sources: List<RenderPipeline> get() = listOf(
        LuminRenderPipelines.UI_SHAPE, LuminRenderPipelines.SHADOW, LuminRenderPipelines.SEGMENTED_SHADOW,
        LuminRenderPipelines.TRIANGLE, LuminRenderPipelines.ARC, LuminRenderPipelines.TEXTURE,
        LuminRenderPipelines.TTF_FONT_AA, LuminRenderPipelines.TTF_FONT_NO_AA,
        LuminRenderPipelines.TTF_FONT_BLUR, LuminRenderPipelines.TTF_FONT_GLITCH,
    )
    private val variants = HashMap<RenderPipeline, RenderPipeline>()
    private val properties = VertexFormat.builder(1)
        .addAttribute("UiRow0", GpuFormat.RGBA32_FLOAT)
        .addAttribute("UiRow1", GpuFormat.RGBA32_FLOAT)
        .addAttribute("UiTint", GpuFormat.RGBA32_FLOAT).build()

    fun variant(source: RenderPipeline): RenderPipeline = variants.getOrPut(source) {
        val format = checkNotNull(source.getVertexFormatBinding(0))
        val builder = RenderPipeline.builder()
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/retained/" + source.location.path))
            .withVertexShader(source.shaders.getValue(ShaderType.VERTEX))
            .withFragmentShader(source.shaders.getValue(ShaderType.FRAGMENT))
            .withVertexBinding(0, format).withVertexBinding(1, properties)
            .withPrimitiveTopology(source.primitiveTopology)
            .withDepthStencilState(Optional.ofNullable(source.depthStencilState))
            .withCull(source.isCull).withPolygonMode(source.polygonMode)
            .withShaderDefine("EPSILON_RETAINED")
            .withShaderDefine("UI_PROPERTY_LOCATION", format.elements.size)
            .withShaderDefine("UI_PROPERTY_ROW1_LOCATION", format.elements.size + 1)
            .withShaderDefine("UI_PROPERTY_TINT_LOCATION", format.elements.size + 2)
        source.bindGroupLayouts.forEach { builder.withBindGroupLayout(it) }
        source.colorTargetStates.forEachIndexed { index, state ->
            if (state != null) builder.withColorTargetState(index, state) else builder.withUnusedColorTargetState(index)
        }
        source.shaderDefines.flags().forEach { builder.withShaderDefine(it) }
        source.shaderDefines.values().forEach { (name, value) -> builder.withShaderDefine(name, value.toInt()) }
        builder.build()
    }
}
