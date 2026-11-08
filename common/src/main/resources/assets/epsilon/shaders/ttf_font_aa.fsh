#version 410 core

layout(location = 0) in vec4 v_Color;
layout(location = 1) in vec2 v_TexCoord;

#include <epsilon:ui_font_texture.glsl>

layout(location = 0) out vec4 f_Color;

const float EDGE_THRESHOLD = 0.5;

float coverage(vec2 uv, float aa) {
    float d = 1.0 - epsilonFontDistance(uv);
    return smoothstep(EDGE_THRESHOLD - aa, EDGE_THRESHOLD + aa, d);
}

#ifdef EPSILON_RETAINED
layout(location = 7) flat in vec4 epsilonTint;
#define main epsilonOriginalMain
#endif

void main() {
    float distance = 1.0 - epsilonFontDistance(v_TexCoord);
    vec2 dx = dFdx(v_TexCoord);
    vec2 dy = dFdy(v_TexCoord);

    float aa = clamp(fwidth(distance) * 0.5, 0.0008, 0.5);

    const vec2 o0 = vec2(0.125, 0.375);
    const vec2 o1 = vec2(0.375, -0.125);
    const vec2 o2 = vec2(-0.125, -0.375);
    const vec2 o3 = vec2(-0.375, 0.125);

    float alpha = coverage(v_TexCoord + dx * o0.x + dy * o0.y, aa);
    alpha += coverage(v_TexCoord + dx * o1.x + dy * o1.y, aa);
    alpha += coverage(v_TexCoord + dx * o2.x + dy * o2.y, aa);
    alpha += coverage(v_TexCoord + dx * o3.x + dy * o3.y, aa);
    alpha *= 0.25;

    f_Color = vec4(v_Color.rgb, v_Color.a * alpha);

    if (f_Color.a < 0.005) {
        discard;
    }
}

#ifdef EPSILON_RETAINED
#undef main
void main() {
    epsilonOriginalMain();
    f_Color *= epsilonTint;
}
#endif
