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
    bool useTargetColors = TimeDirection.w > 0.5;
    vec2 oneTexel = 1.0 / max(InSize.xy, vec2(1.0));
    float sigma = max(radius * 0.5, 0.5);

    vec3 colorSum = vec3(0.0);
    float alphaSum = 0.0;
    float totalWeight = 0.0;
    float centerWeight = 1.0 / (2.50662827463 * sigma);
    vec4 center = texture(InputSampler, texCoord);
    colorSum += center.rgb * center.a * centerWeight;
    alphaSum += center.a * centerWeight;
    totalWeight += centerWeight;

    for (float i = 1.0; i <= radius; i += 1.0) {
        float weight = (1.0 / (2.50662827463 * sigma)) * exp(-(i * i) / (2.0 * sigma * sigma));
        vec2 offset = direction * i * oneTexel;
        vec4 positive = texture(InputSampler, texCoord + offset);
        vec4 negative = texture(InputSampler, texCoord - offset);
        colorSum += positive.rgb * positive.a * weight;
        colorSum += negative.rgb * negative.a * weight;
        alphaSum += positive.a * weight;
        alphaSum += negative.a * weight;
        totalWeight += weight * 2.0;
    }

    float blurredAlpha = alphaSum / max(totalWeight, 1.0e-6);
    if (blurredAlpha <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 sourceColor = colorSum / max(alphaSum, 1.0e-6);
    vec3 finalColor = Color.rgb;
    bool horizontalPass = abs(direction.x) > 0.5;
    if (useTargetColors && horizontalPass) {
        finalColor = sourceColor;
    } else if (gradient) {
        float position = 1.0 - gl_FragCoord.y / max(Resolution.y, 1.0);
        float factor = sin(position * 6.28318530 + time * gradientSpeed * 0.5) * 0.5 + 0.5;
        vec3 gradientColor = mix(Color1.rgb, Color2.rgb, factor);
        float customColor = useTargetColors ? step(1.0e-3, distance(sourceColor, Color.rgb)) : 0.0;
        finalColor = mix(gradientColor, sourceColor, customColor);
    } else if (useTargetColors) {
        finalColor = sourceColor;
    }

    fragColor = vec4(finalColor, clamp(blurredAlpha * exposure, 0.0, 1.0));
}
