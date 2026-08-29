#version 410 core

uniform sampler2D InputSampler;

layout(std140) uniform BlurUniforms {
    vec3 InputInfo;
    vec4 Rect;
    vec4 CornerRadii;
    vec4 SegmentInfo;
    vec4 SegmentRects[64];
    vec4 SegmentRadii[64];
};

layout(location = 0) out vec4 fragColor;

float roundRectDistance(vec2 position, vec4 innerRect, vec4 radius) {
    vec2 halfSize = (innerRect.zw - innerRect.xy) * 0.5;
    vec2 center = (innerRect.xy + innerRect.zw) * 0.5;
    vec2 p = position - center;

    vec2 s = step(0.0, p);
    float rCurrent = mix(
        mix(radius.x, radius.w, s.y),
        mix(radius.y, radius.z, s.y),
        s.x
    );

    vec2 q = abs(p) - halfSize + rCurrent;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rCurrent;
}

float shapeCoverage(float distance) {
    float delta = max(fwidth(distance), 1e-4);
    return 1.0 - smoothstep(-delta, delta, distance);
}

float segmentedCoverage(vec2 position, int count) {
    float coverage = 0.0;
    for (int i = 0; i < 64; i++) {
        if (i >= count) break;

        vec4 rect = SegmentRects[i];
        vec4 bounds = vec4(rect.xy, rect.xy + rect.zw);
        float radius = max(0.0, SegmentRadii[i].x);
        coverage = min(1.0, coverage + shapeCoverage(roundRectDistance(position, bounds, vec4(radius))));
    }
    return coverage;
}

vec4 blur() {
    #define TAU 6.28318530718

    vec2 inputResolution = InputInfo.xy;
    float quality = InputInfo.z;
    vec2 radius = quality / inputResolution.xy;

    vec2 uv = gl_FragCoord.xy / inputResolution;
    vec4 sampleColor = texture(InputSampler, uv);
    vec3 colorSum = sampleColor.rgb;
    vec3 premultipliedColorSum = sampleColor.rgb * sampleColor.a;
    float alphaSum = sampleColor.a;

    float step =  TAU / 16.0;

    for (float d = 0.0; d < TAU; d += step) {
        for (float i = 0.2; i <= 1.0; i += 0.2) {
            sampleColor = texture(InputSampler, uv + vec2(cos(d), sin(d)) * radius * i);
            colorSum += sampleColor.rgb;
            premultipliedColorSum += sampleColor.rgb * sampleColor.a;
            alphaSum += sampleColor.a;
        }
    }

    vec3 color = alphaSum > 1e-5 ? premultipliedColorSum / alphaSum : colorSum / 81.0;
    return vec4(color, alphaSum / 81.0);
}

void main() {
    vec2 uSize = Rect.xy;
    vec2 uLocation = Rect.zw;
    vec4 radii = CornerRadii;
    vec4 bounds = vec4(uLocation, uLocation + uSize);

    int segmentCount = int(SegmentInfo.x);
    float alpha = segmentCount > 0
        ? segmentedCoverage(gl_FragCoord.xy, segmentCount)
        : shapeCoverage(roundRectDistance(gl_FragCoord.xy, bounds, radii));

    fragColor = vec4(blur().rgb, alpha);
    if (alpha < 0.001) discard;
}
