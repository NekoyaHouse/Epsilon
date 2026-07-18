#version 410 core

uniform sampler2D InputSampler;

layout(std140) uniform FilterColor {
    vec4 TintColor;
};

in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 sceneColor = texture(InputSampler, texCoord);
    vec3 tinted = mix(sceneColor.rgb, TintColor.rgb, clamp(TintColor.a, 0.0, 1.0));
    fragColor = vec4(tinted, sceneColor.a);
}
