#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InputSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
#if SSAA_SCALE == 4
    vec2 texel = 1.0 / vec2(textureSize(InputSampler, 0));
    fragColor = 0.25 * (
        texture(InputSampler, texCoord + vec2(-texel.x, -texel.y)) +
        texture(InputSampler, texCoord + vec2( texel.x, -texel.y)) +
        texture(InputSampler, texCoord + vec2(-texel.x,  texel.y)) +
        texture(InputSampler, texCoord + vec2( texel.x,  texel.y))
    );
#else
    fragColor = texture(InputSampler, texCoord);
#endif
}
