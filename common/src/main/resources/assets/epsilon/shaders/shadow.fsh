#version 410 core

in vec2 f_Position;
in vec4 f_Color;
in vec4 f_InnerRect;
in vec4 f_Radius;
in float f_BlurRadius;

layout(location = 0) out vec4 fragColor;

const int DIRECTION_PAIR_COUNT = 8;
const int SAMPLE_COUNT = 5;
const float SAMPLE_WEIGHT = 1.0 / 81.0;
const vec2 SAMPLE_DIRECTIONS[DIRECTION_PAIR_COUNT] = vec2[DIRECTION_PAIR_COUNT](
    vec2(1.0, 0.0),
    vec2(0.92387953, 0.38268343),
    vec2(0.70710678, 0.70710678),
    vec2(0.38268343, 0.92387953),
    vec2(0.0, 1.0),
    vec2(-0.38268343, 0.92387953),
    vec2(-0.70710678, 0.70710678),
    vec2(-0.92387953, 0.38268343)
);

float roundedRectDistance(vec2 position, vec2 center, vec2 halfSize) {
    vec2 local = position - center;
    vec2 side = step(0.0, local);

    float radius = mix(
        mix(f_Radius.x, f_Radius.w, side.y),
        mix(f_Radius.y, f_Radius.z, side.y),
        side.x
    );

    vec2 distance = abs(local) - halfSize + radius;
    return length(max(distance, 0.0)) + min(max(distance.x, distance.y), 0.0) - radius;
}

float maskAt(vec2 position, vec2 center, vec2 halfSize, float antialias) {
    return 1.0 - smoothstep(0.0, antialias, roundedRectDistance(position, center, halfSize));
}

void main() {
    vec2 positionWidth = fwidth(f_Position);
    float antialias = max(max(positionWidth.x, positionWidth.y) * 2.0, 0.0001);
    vec2 halfSize = (f_InnerRect.zw - f_InnerRect.xy) * 0.5;
    vec2 center = (f_InnerRect.xy + f_InnerRect.zw) * 0.5;
    float baseDistance = roundedRectDistance(f_Position, center, halfSize);
    float edgeAntialias = max(fwidth(baseDistance), 0.0001);
    if (baseDistance <= -edgeAntialias || baseDistance >= f_BlurRadius) discard;

    float originalMask = 1.0 - smoothstep(0.0, antialias, baseDistance);
    float blurredMask = originalMask;

    for (int directionIndex = 0; directionIndex < DIRECTION_PAIR_COUNT; directionIndex++) {
        vec2 direction = SAMPLE_DIRECTIONS[directionIndex];

        for (int sampleIndex = 1; sampleIndex <= SAMPLE_COUNT; sampleIndex++) {
            float sampleScale = float(sampleIndex) / float(SAMPLE_COUNT);
            vec2 offset = direction * f_BlurRadius * sampleScale;
            blurredMask += maskAt(f_Position + offset, center, halfSize, antialias);
            blurredMask += maskAt(f_Position - offset, center, halfSize, antialias);
        }
    }

    blurredMask *= SAMPLE_WEIGHT;
    float outsideCoverage = smoothstep(-edgeAntialias, edgeAntialias, baseDistance);
    float fadeWidth = min(antialias, f_BlurRadius);
    float rangeCoverage = 1.0 - smoothstep(f_BlurRadius - fadeWidth, f_BlurRadius, max(baseDistance, 0.0));
    float outsideAlpha = clamp(blurredMask * outsideCoverage * rangeCoverage, 0.0, 1.0);
    float alpha = f_Color.a * outsideAlpha;
    if (alpha < 0.001) discard;

    fragColor = vec4(f_Color.rgb, alpha);
}
