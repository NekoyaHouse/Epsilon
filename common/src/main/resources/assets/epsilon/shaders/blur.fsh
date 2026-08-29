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

vec4 blur() {
    #define TAU 6.28318530718

    vec2 inputResolution = InputInfo.xy;
    float quality = InputInfo.z;
    vec2 radius = quality / inputResolution.xy;

    vec2 uv = gl_FragCoord.xy / inputResolution;
    vec4 sampleColor = texture(InputSampler, uv);
    vec3 centerColor = sampleColor.rgb;
    float centerAlpha = sampleColor.a;
    vec3 colorSum = centerColor;

    float step =  TAU / 16.0;

    for (float d = 0.0; d < TAU; d += step) {
        for (float i = 0.2; i <= 1.0; i += 0.2) {
            sampleColor = texture(InputSampler, uv + vec2(cos(d), sin(d)) * radius * i);
            float signal = max(sampleColor.a, max(sampleColor.r, max(sampleColor.g, sampleColor.b)));
            colorSum += signal > 1e-5 ? sampleColor.rgb : centerColor;
        }
    }

    return vec4(colorSum / 81.0, centerAlpha);
}

void main() {
    vec2 uSize = Rect.xy;
    vec2 uLocation = Rect.zw;
    vec4 radii = CornerRadii;
    vec4 bounds = vec4(uLocation, uLocation + uSize);

    int segmentCount = int(SegmentInfo.x);
    int shapeCount = max(segmentCount, 1);
    float alpha = 0.0;

    for (int i = 0; i < 64; i++) {
        if (i >= shapeCount) break;

        vec4 shapeBounds = bounds;
        vec4 shapeRadii = radii;
        if (segmentCount > 0) {
            vec4 rect = SegmentRects[i];
            shapeBounds = vec4(rect.xy, rect.xy + rect.zw);
            shapeRadii = vec4(max(0.0, SegmentRadii[i].x));
        }

        vec2 halfSize = (shapeBounds.zw - shapeBounds.xy) * 0.5;
        vec2 center = (shapeBounds.xy + shapeBounds.zw) * 0.5;
        vec2 position = gl_FragCoord.xy - center;
        vec2 quadrant = step(0.0, position);
        float radius = mix(mix(shapeRadii.x, shapeRadii.w, quadrant.y), mix(shapeRadii.y, shapeRadii.z, quadrant.y), quadrant.x);
        vec2 distanceToEdge = abs(position) - halfSize + radius;
        float distance = length(max(distanceToEdge, 0.0)) + min(max(distanceToEdge.x, distanceToEdge.y), 0.0) - radius;
        float delta = max(fwidth(distance), 1e-4);
        alpha = min(1.0, alpha + 1.0 - smoothstep(-delta, delta, distance));
    }

    if (alpha < 0.001) discard;
    fragColor = vec4(blur().rgb, alpha);
}
