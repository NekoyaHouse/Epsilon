#version 410 core

in vec2 f_Position;

layout(std140) uniform SegmentedShadowUniforms {
    vec4 ShadowColor;
    vec4 ShadowInfo;
    vec4 SegmentRects[64];
    vec4 SegmentRadii[64];
    vec4 SegmentColors[64];
};

layout(location = 0) out vec4 fragColor;

const int DIRECTION_PAIR_COUNT = 8;
const int SAMPLE_COUNT = 5;
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

float roundedRectDistance(vec2 position, vec4 rect, float radius) {
    vec2 halfSize = rect.zw * 0.5;
    vec2 center = rect.xy + halfSize;
    vec2 distance = abs(position - center) - halfSize + radius;
    return length(max(distance, 0.0)) + min(max(distance.x, distance.y), 0.0) - radius;
}

float segmentedDistance(vec2 position, int count) {
    float distance = 1e20;
    for (int i = 0; i < 64; i++) {
        if (i >= count) break;
        distance = min(distance, roundedRectDistance(position, SegmentRects[i], max(0.0, SegmentRadii[i].x)));
    }
    return distance;
}

vec3 shadowColorAt(float y, int count) {
    if (count <= 1) return SegmentColors[0].rgb;

    float firstCenter = SegmentRects[0].y + SegmentRects[0].w * 0.5;
    if (y <= firstCenter) return SegmentColors[0].rgb;

    for (int i = 0; i < 63; i++) {
        if (i + 1 >= count) break;
        float currentCenter = SegmentRects[i].y + SegmentRects[i].w * 0.5;
        float nextCenter = SegmentRects[i + 1].y + SegmentRects[i + 1].w * 0.5;
        if (y <= nextCenter) {
            float progress = clamp((y - currentCenter) / max(nextCenter - currentCenter, 0.0001), 0.0, 1.0);
            return mix(SegmentColors[i].rgb, SegmentColors[i + 1].rgb, progress);
        }
    }

    return SegmentColors[count - 1].rgb;
}

float maskAt(vec2 position, int count, float antialias) {
    return 1.0 - smoothstep(0.0, antialias, segmentedDistance(position, count));
}

void main() {
    int count = int(ShadowInfo.y);
    float blurRadius = max(0.0, ShadowInfo.x);
    if (count <= 0 || blurRadius <= 0.0) discard;

    vec2 positionWidth = fwidth(f_Position);
    float antialias = max(max(positionWidth.x, positionWidth.y) * 2.0, 0.0001);
    float baseDistance = segmentedDistance(f_Position, count);
    float edgeAntialias = max(max(positionWidth.x, positionWidth.y), 0.0001);
    if (baseDistance >= blurRadius) discard;

    float blurredMask = maskAt(f_Position, count, antialias);

    for (int directionIndex = 0; directionIndex < DIRECTION_PAIR_COUNT; directionIndex++) {
        vec2 direction = SAMPLE_DIRECTIONS[directionIndex];
        for (int sampleIndex = 1; sampleIndex <= SAMPLE_COUNT; sampleIndex++) {
            float sampleScale = float(sampleIndex) / float(SAMPLE_COUNT);
            vec2 offset = direction * blurRadius * sampleScale;
            blurredMask += maskAt(f_Position + offset, count, antialias);
            blurredMask += maskAt(f_Position - offset, count, antialias);
        }
    }

    blurredMask *= 1.0 / 81.0;
    float outsideCoverage = step(0.0, baseDistance);
    float fadeWidth = min(antialias, blurRadius);
    float rangeCoverage = 1.0 - smoothstep(blurRadius - fadeWidth, blurRadius, max(baseDistance, 0.0));
    float alpha = ShadowColor.a * clamp(blurredMask * outsideCoverage * rangeCoverage, 0.0, 1.0);
    if (alpha < 0.001) discard;

    fragColor = vec4(shadowColorAt(f_Position.y, count), alpha);
}
