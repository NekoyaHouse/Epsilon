package com.github.epsilon.graphics

import com.mojang.renderpearl.api.pipeline.BindGroupLayout
import com.mojang.renderpearl.api.pipeline.UniformType

class LuminBindGroupLayouts {

    companion object {

        @JvmField
        val FONT_BLUR: BindGroupLayout = uniform("FontBlurUniforms")

        @JvmField
        val GLITCH_DATA: BindGroupLayout = uniform("GlitchData")

        @JvmField
        val SEGMENTED_SHADOW: BindGroupLayout = uniform("SegmentedShadowUniforms")

        @JvmField
        val GLSL_SANDBOX_INFO: BindGroupLayout = uniform("GlslSandboxInfo")

        @JvmField
        val FXAA_INFO: BindGroupLayout = uniform("FxaaInfo")

        @JvmField
        val FILTER_COLOR: BindGroupLayout = uniform("FilterColor")

        @JvmField
        val CUSTOM_SKY: BindGroupLayout = uniform("CustomSky")

        @JvmField
        val BLUR: BindGroupLayout = uniform("BlurUniforms")

        @JvmField
        val BOX_BLUR: BindGroupLayout = uniform("BoxBlurUniforms")

        @JvmField
        val GLOW_CONFIG: BindGroupLayout = uniform("GlowConfig")

        @JvmField
        val SHADER_PARAMS: BindGroupLayout = uniform("ShaderParams")

        @JvmField
        val SHADER_COLORS: BindGroupLayout = uniform("ShaderColors")

        @JvmField
        val MOTION_BLUR_DATA: BindGroupLayout = uniform("MotionBlurData")

        @JvmField
        val INPUT_SAMPLER: BindGroupLayout =
            BindGroupLayout.builder().withUniform("InputSampler", UniformType.COMBINED_IMAGE_SAMPLER).build()

        @JvmField
        val MOTION_BLUR_TEXTURES: BindGroupLayout = BindGroupLayout.builder()
            .withUniform("InputSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .build()

        private fun uniform(name: String): BindGroupLayout =
            BindGroupLayout.builder().withUniform(name, UniformType.UNIFORM_BUFFER).build()
    }
}
