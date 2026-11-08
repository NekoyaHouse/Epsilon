#version 410 core

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <epsilon:ui_properties.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec4 v_Color;
layout(location = 1) out vec2 v_TexCoord;
#ifdef EPSILON_TEXTURE_TABLE
layout(location = 6) flat out float epsilonTextureSlot;
#endif

void main() {
#ifdef EPSILON_RETAINED
    epsilonTint = UiTint;
#ifdef EPSILON_TEXTURE_TABLE
    epsilonTextureSlot = UiRow0.w;
#endif
#endif
    gl_Position = ProjMat * ModelViewMat * epsilonPosition(vec4(Position, 1.0));

    v_Color = Color;
    v_TexCoord = UV0;
}
