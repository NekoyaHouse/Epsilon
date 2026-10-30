package com.github.epsilon.graphics

import com.mojang.renderpearl.api.pipeline.BindGroupLayout
import com.mojang.renderpearl.api.pipeline.UniformType

object LuminBindGroupLayouts {

    val FONT_BLUR: BindGroupLayout = uniform("FontBlurUniforms")

    val GLITCH_DATA: BindGroupLayout = uniform("GlitchData")

    val SEGMENTED_SHADOW: BindGroupLayout = uniform("SegmentedShadowUniforms")

    val GLSL_SANDBOX_INFO: BindGroupLayout = uniform("GlslSandboxInfo")

    val FXAA_INFO: BindGroupLayout = uniform("FxaaInfo")

    val FILTER_COLOR: BindGroupLayout = uniform("FilterColor")

    val CUSTOM_SKY: BindGroupLayout = uniform("CustomSky")

    val BLUR: BindGroupLayout = uniform("BlurUniforms")

    val BOX_BLUR: BindGroupLayout = uniform("BoxBlurUniforms")

    val GLOW_CONFIG: BindGroupLayout = uniform("GlowConfig")

    val SHADER_PARAMS: BindGroupLayout = uniform("ShaderParams")

    val SHADER_COLORS: BindGroupLayout = uniform("ShaderColors")

    val MOTION_BLUR_DATA: BindGroupLayout = uniform("MotionBlurData")

    val INPUT_SAMPLER: BindGroupLayout =
        BindGroupLayout.builder().withUniform("InputSampler", UniformType.COMBINED_IMAGE_SAMPLER).build()

    val MOTION_BLUR_TEXTURES: BindGroupLayout = BindGroupLayout.builder()
        .withUniform("InputSampler", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
        .build()

    private fun uniform(name: String): BindGroupLayout =
        BindGroupLayout.builder().withUniform(name, UniformType.UNIFORM_BUFFER).build()
}
