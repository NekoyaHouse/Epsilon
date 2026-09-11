package com.github.epsilon.graphics;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.shaders.UniformType;

public class LuminBindGroupLayouts {

    public static final BindGroupLayout FONT_BLUR = uniform("FontBlurUniforms");
    public static final BindGroupLayout GLITCH_DATA = uniform("GlitchData");
    public static final BindGroupLayout SEGMENTED_SHADOW = uniform("SegmentedShadowUniforms");
    public static final BindGroupLayout GLSL_SANDBOX_INFO = uniform("GlslSandboxInfo");
    public static final BindGroupLayout FXAA_INFO = uniform("FxaaInfo");
    public static final BindGroupLayout FILTER_COLOR = uniform("FilterColor");
    public static final BindGroupLayout CUSTOM_SKY = uniform("CustomSky");
    public static final BindGroupLayout BLUR = uniform("BlurUniforms");
    public static final BindGroupLayout BOX_BLUR = uniform("BoxBlurUniforms");
    public static final BindGroupLayout GLOW_CONFIG = uniform("GlowConfig");
    public static final BindGroupLayout SHADER_PARAMS = uniform("ShaderParams");
    public static final BindGroupLayout SHADER_COLORS = uniform("ShaderColors");
    public static final BindGroupLayout MOTION_BLUR_DATA = uniform("MotionBlurData");
    public static final BindGroupLayout INPUT_SAMPLER = BindGroupLayout.builder().withSampler("InputSampler").build();
    public static final BindGroupLayout MOTION_BLUR_TEXTURES = BindGroupLayout.builder().withSampler("InputSampler").withSampler("DepthSampler").build();

    private static BindGroupLayout uniform(String name) {
        return BindGroupLayout.builder().withUniform(name, UniformType.UNIFORM_BUFFER).build();
    }

}
