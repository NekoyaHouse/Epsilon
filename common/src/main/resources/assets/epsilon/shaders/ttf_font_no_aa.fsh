#version 410 core

layout(location = 0) in vec4 v_Color;
layout(location = 1) in vec2 v_TexCoord;

#include <epsilon:ui_font_texture.glsl>

layout(location = 0) out vec4 f_Color;

#ifdef EPSILON_RETAINED
layout(location = 7) flat in vec4 epsilonTint;
#define main epsilonOriginalMain
#endif

void main() {
    float alpha = epsilonFontCoverage(v_TexCoord, epsilonFontScreenRange(v_TexCoord));

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
