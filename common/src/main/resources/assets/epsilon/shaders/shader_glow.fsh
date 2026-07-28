#version 410 core

uniform sampler2D InputSampler;

layout(std140) uniform GlowConfig {
    int GlowStrength;
    float GlowMultiplier;
    int GlowQuality;
};

in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

struct BlurResult {
    float strength;
    vec3 color;
};

BlurResult computeBlur() {
    int radius = GlowQuality * GlowStrength;
    float alphaSum = 0.0;
    vec3 rgbSum = vec3(0.0);
    float stepSize = float(GlowQuality);
    float radiusFloat = float(radius);
    vec2 texelSize = 1.0 / vec2(textureSize(InputSampler, 0));

    for (float x = -radiusFloat; x <= radiusFloat; x += stepSize) {
        for (float y = -radiusFloat; y <= radiusFloat; y += stepSize) {
            vec4 sampleColor = texture(InputSampler, texCoord + texelSize * vec2(x, y));
            alphaSum += sampleColor.a;
            rgbSum += sampleColor.rgb * sampleColor.a;
        }
    }

    float normalization = float(((GlowStrength * GlowStrength) + GlowStrength) * 4);
    float blurStrength = clamp(alphaSum / normalization, 0.0, 1.0) * GlowMultiplier;
    vec3 blurColor = alphaSum > 0.0 ? rgbSum / alphaSum : vec3(0.0);
    return BlurResult(blurStrength, blurColor);
}

void main() {
    vec4 center = texture(InputSampler, texCoord);

    if (center.a > 0.0) {
        BlurResult blur = computeBlur();
        fragColor = mix(center, vec4(center.rgb, 1.0), GlowMultiplier - blur.strength);
        return;
    }

    vec2 texelSize = 1.0 / vec2(textureSize(InputSampler, 0));
    for (int x = -1; x <= 1; ++x) {
        for (int y = -1; y <= 1; ++y) {
            if (x == 0 && y == 0) {
                continue;
            }

            vec4 neighbor = texture(InputSampler, texCoord + texelSize * vec2(x, y));
            if (neighbor.a > 0.0) {
                fragColor = vec4(neighbor.rgb, 1.0);
                return;
            }
        }
    }

    BlurResult blur = computeBlur();
    fragColor = vec4(blur.color, blur.strength);
}
