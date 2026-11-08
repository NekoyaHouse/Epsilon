#version 410 core

layout(location = 0) in vec2 f_Position;
layout(location = 1) in vec4 f_Color;
layout(location = 2) in vec4 f_InnerRect;
layout(location = 3) in vec4 f_Radius;
layout(location = 4) in float f_OutlineWidth;

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

#ifdef EPSILON_RETAINED
layout(location = 7) flat in vec4 epsilonTint;
#define main epsilonOriginalMain
#endif

void main() {
    if (f_OutlineWidth < -1.5) {
        vec2 a = f_InnerRect.xy;
        vec2 b = f_InnerRect.zw;
        vec2 c = f_Radius.xy;
        vec2 ab = b - a;
        vec2 ac = c - a;
        vec2 ap = f_Position - a;
        float det = ab.x * ac.y - ab.y * ac.x;
        if (abs(det) < 0.000001) discard;
        float v = (ap.x * ac.y - ap.y * ac.x) / det;
        float w = (ab.x * ap.y - ab.y * ap.x) / det;
        float d = min(min(v, w), 1.0 - v - w);
        float alpha = smoothstep(0.0, max(fwidth(d), 0.000001), d);
        fragColor = vec4(f_Color.rgb, f_Color.a * alpha);
        if (fragColor.a < 0.001) discard;
        return;
    }
    float dist = roundRectDistance(f_Position, f_InnerRect, f_Radius);
    float halfOutline = max(f_OutlineWidth * 0.5, 0.0);
    float delta = max(fwidth(dist), 0.0001);
    float alpha = f_OutlineWidth < 0.0
        ? 1.0 - smoothstep(-delta, delta, dist)
        : 1.0 - smoothstep(halfOutline - delta, halfOutline + delta, abs(dist));

    fragColor = vec4(f_Color.rgb, f_Color.a * alpha);
    if (fragColor.a < 0.001) discard;
}

#ifdef EPSILON_RETAINED
#undef main
void main() {
    epsilonOriginalMain();
    fragColor *= epsilonTint;
}
#endif
