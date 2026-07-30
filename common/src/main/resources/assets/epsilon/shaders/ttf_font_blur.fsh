#version 410 core

in vec4 v_Color;
in vec2 v_TexCoord;
flat in vec4 v_GlyphUvBounds;

uniform sampler2D Sampler0;

layout(std140) uniform FontBlurUniforms {
    vec4 FontBlurInfo;
};

layout(location = 0) out vec4 f_Color;

const float EDGE_THRESHOLD = 0.5;
const float NANOVG_SIGMA_SCALE = 0.57735;
const float ATLAS_SIZE = 1024.0;
const float SDF_PADDING = 16.0;
const float SDF_DISTANCE_PER_ATLAS_PIXEL = EDGE_THRESHOLD / SDF_PADDING;
const float SQRT_TWO = 1.41421356237;

float erfApprox(float value) {
    float signValue = value < 0.0 ? -1.0 : 1.0;
    float x = abs(value);
    float t = 1.0 / (1.0 + 0.3275911 * x);
    float polynomial = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
        - 0.284496736) * t + 0.254829592) * t;
    return signValue * (1.0 - polynomial * exp(-x * x));
}

float signedDistancePixels(vec2 uv, vec2 screenDx, vec2 screenDy) {
    vec2 clampedUv = clamp(uv, v_GlyphUvBounds.xy, v_GlyphUvBounds.zw);
    float encodedDistance = 1.0 - texture(Sampler0, clampedUv).r;
    float atlasPixelsPerScreenPixel = max(length(screenDx), length(screenDy)) * ATLAS_SIZE;
    float encodedDistancePerScreenPixel = max(
        atlasPixelsPerScreenPixel * SDF_DISTANCE_PER_ATLAS_PIXEL,
        0.0001
    );
    float distance = (encodedDistance - EDGE_THRESHOLD) / encodedDistancePerScreenPixel;

    vec2 uvPerScreenPixel = max(vec2(length(screenDx), length(screenDy)), vec2(0.0000001));
    vec2 outsideDistance = abs(uv - clampedUv) / uvPerScreenPixel;
    return distance - length(outsideDistance);
}

void main() {
    float blurRadius = max(FontBlurInfo.x, 0.001);
    vec2 screenDx = dFdx(v_TexCoord);
    vec2 screenDy = dFdy(v_TexCoord);
    float distance = signedDistancePixels(v_TexCoord, screenDx, screenDy);
    float blurSigma = blurRadius * NANOVG_SIGMA_SCALE;
    float effectiveSigma = sqrt(blurSigma * blurSigma + 0.25);
    float coverage = 0.5 + 0.5 * erfApprox(distance / (SQRT_TWO * effectiveSigma));
    float alpha = v_Color.a * clamp(coverage, 0.0, 1.0);
    if (alpha < 0.001) {
        discard;
    }

    f_Color = vec4(v_Color.rgb, alpha);
}
