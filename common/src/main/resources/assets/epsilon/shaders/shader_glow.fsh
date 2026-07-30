#version 410 core

uniform sampler2D InputSampler;

layout(std140) uniform GlowConfig {
    vec4 InSize;
    vec4 Resolution;
    vec4 Color;
    vec4 Color1;
    vec4 Color2;
    vec4 GlowParams;
    vec4 TimeDirection;
};

in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    float radius = max(1.0, GlowParams.x);
    float exposure = max(0.0, GlowParams.y);
    float gradientSpeed = GlowParams.z;
    bool gradient = GlowParams.w > 0.5;
    float time = TimeDirection.x;
    vec2 direction = TimeDirection.yz;
    vec2 oneTexel = 1.0 / max(InSize.xy, vec2(1.0));
    float sigma = max(radius * 0.5, 0.5);

    vec4 sum = vec4(0.0);
    float totalWeight = 0.0;
    float centerWeight = 1.0 / (2.50662827463 * sigma);
    sum += texture(InputSampler, texCoord) * centerWeight;
    totalWeight += centerWeight;

    for (float i = 1.0; i <= radius; i += 1.0) {
        float weight = (1.0 / (2.50662827463 * sigma)) * exp(-(i * i) / (2.0 * sigma * sigma));
        vec2 offset = direction * i * oneTexel;
        sum += texture(InputSampler, texCoord + offset) * weight;
        sum += texture(InputSampler, texCoord - offset) * weight;
        totalWeight += weight * 2.0;
    }

    vec4 blurred = sum / max(totalWeight, 1.0e-6);
    if (blurred.a <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 finalColor = Color.rgb;
    if (gradient) {
        float position = 1.0 - gl_FragCoord.y / max(Resolution.y, 1.0);
        float factor = sin(position * 6.28318530 + time * gradientSpeed * 0.5) * 0.5 + 0.5;
        finalColor = mix(Color1.rgb, Color2.rgb, factor);
    }

    fragColor = vec4(finalColor, clamp(blurred.a * exposure, 0.0, 1.0));
}
