#version 410 core

layout(location = 0) in vec2 f_Position;
layout(location = 1) in vec4 f_StartColor;
layout(location = 2) in vec4 f_MiddleColor;
layout(location = 3) in vec4 f_EndColor;
layout(location = 4) in vec4 f_Circle;
layout(location = 5) in vec4 f_Sweep;

layout(location = 0) out vec4 fragColor;

const float TAU = 6.28318530718;

float sdSegment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

#ifdef EPSILON_RETAINED
layout(location = 7) flat in vec4 epsilonTint;
#define main epsilonOriginalMain
#endif

void main() {
    vec2 center = f_Circle.xy;
    float radius = f_Circle.z;
    float halfStroke = f_Circle.w;

    float start = f_Sweep.x;
    float sweep = f_Sweep.y;
    bool roundCap = f_Sweep.z > 0.5;
    float gradientRotation = f_Sweep.w;

    vec2 p = f_Position - center;
    float angle = atan(p.y, p.x);

    float ring = abs(length(p) - radius) - halfStroke;

    float dist;
    if (sweep >= TAU - 0.0001) {
        dist = ring;
    } else {
        float rel = mod(angle - start, TAU);

        if (rel <= sweep) {
            dist = ring;
        } else {
            float gapMid = sweep + (TAU - sweep) * 0.5;
            float capAngle = rel < gapMid ? start + sweep : start;
            vec2 dir = vec2(cos(capAngle), sin(capAngle));

            if (roundCap) {
                dist = length(p - dir * radius) - halfStroke;
            } else {
                dist = sdSegment(p, dir * (radius - halfStroke), dir * (radius + halfStroke));
            }
        }
    }

    float delta = fwidth(dist);
    float alpha = 1.0 - smoothstep(-delta, delta, dist);

    float gradientPosition = mod(angle - gradientRotation, TAU) / TAU;
    vec4 gradientColor = gradientPosition <= 0.5
        ? mix(f_StartColor, f_MiddleColor, gradientPosition * 2.0)
        : mix(f_MiddleColor, f_EndColor, (gradientPosition - 0.5) * 2.0);

    fragColor = vec4(gradientColor.rgb, gradientColor.a * alpha);
    if (alpha < 0.001) discard;
}

#ifdef EPSILON_RETAINED
#undef main
void main() {
    epsilonOriginalMain();
    fragColor *= epsilonTint;
}
#endif
