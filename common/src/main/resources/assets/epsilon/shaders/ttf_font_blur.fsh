#version 410 core

in vec4 v_Color;
in vec2 v_TexCoord;
flat in vec4 v_GlyphUvBounds;

uniform sampler2D Sampler0;

layout(std140) uniform FontBlurUniforms {
    vec4 FontBlurInfo;
};

layout(location = 0) out vec4 f_Color;

const float GLOW_SIGMA_SCALE = 0.65;
const float GLOW_ALPHA_POWER = 1.35;
const float GLOW_ALPHA_SCALE = 0.78;

float glyphAlpha(vec2 uv) {
    vec2 lower = v_GlyphUvBounds.xy;
    vec2 upper = v_GlyphUvBounds.zw;
    if (uv.x < lower.x || uv.y < lower.y || uv.x > upper.x || uv.y > upper.y) {
        return 0.0;
    }
    return texture(Sampler0, uv).r;
}

float blurredAlpha(vec2 uv, vec2 screenDx, vec2 screenDy, float radius) {
    float sigma = max(radius * GLOW_SIGMA_SCALE, 0.5);
    float sampleRadius = max(radius * 1.1, 1.0);
    float total = 0.0;
    float weightTotal = 0.0;

    for (int y = -4; y <= 4; y++) {
        for (int x = -4; x <= 4; x++) {
            vec2 offset = screenDx * (float(x) * sampleRadius * 0.25) + screenDy * (float(y) * sampleRadius * 0.25);
            float distanceSquared = float(x * x + y * y) * sampleRadius * sampleRadius * 0.0625;
            float weight = exp(-distanceSquared / (2.0 * sigma * sigma));
            total += glyphAlpha(uv + offset) * weight;
            weightTotal += weight;
        }
    }

    return total / max(weightTotal, 0.0001);
}

void main() {
    float blurRadius = max(FontBlurInfo.x, 0.001);
    float blurred = blurredAlpha(v_TexCoord, dFdx(v_TexCoord), dFdy(v_TexCoord), blurRadius);
    float glow = pow(clamp(blurred, 0.0, 1.0), GLOW_ALPHA_POWER) * GLOW_ALPHA_SCALE;
    float alpha = v_Color.a * glow;

    if (alpha < 0.001) {
        discard;
    }
    f_Color = vec4(v_Color.rgb, alpha);
}
