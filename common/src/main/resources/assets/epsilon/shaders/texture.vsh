#version 410 core

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <epsilon:ui_properties.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in vec4 InnerRect;
layout(location = 4) in vec4 Radius;

layout(location = 0) out vec2 f_Position;
layout(location = 1) out vec4 f_Color;
layout(location = 2) out vec2 f_TexCoord;
layout(location = 3) out vec4 f_InnerRect;
layout(location = 4) out vec4 f_Radius;

void main() {
#ifdef EPSILON_RETAINED
    epsilonTint = UiTint;
#endif
    gl_Position = ProjMat * ModelViewMat * epsilonPosition(vec4(Position, 1.0));

    f_Position = InnerRect.xy;
    f_Color = Color;
    f_TexCoord = UV0;

    f_InnerRect = vec4(0.0, 0.0, InnerRect.zw);
    f_Radius = Radius;
}
