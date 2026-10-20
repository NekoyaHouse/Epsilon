#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InputSampler;

layout(location = 0) in vec2 texCoord;

void main() {
    ivec2 sourceSize = textureSize(InputSampler, 0);
    ivec2 sourcePos = ivec2(gl_FragCoord.xy) / SSAA_SCALE;
    float depth = texelFetch(InputSampler, clamp(sourcePos, ivec2(0), sourceSize - 1), 0).r;

    if (depth <= 0.0) {
        discard;
    }

    gl_FragDepth = depth;
}
