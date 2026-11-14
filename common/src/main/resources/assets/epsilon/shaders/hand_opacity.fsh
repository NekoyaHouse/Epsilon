#version 410 core

uniform sampler2D InputSampler;

layout(std140) uniform HandOpacity {
    vec4 Opacity;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(texture(InputSampler, texCoord).rgb, 1.0 - Opacity.x);
}
