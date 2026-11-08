#version 410 core

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <epsilon:ui_properties.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 StartColor;
layout(location = 2) in vec4 MiddleColor;
layout(location = 3) in vec4 EndColor;
layout(location = 4) in vec4 Circle;
layout(location = 5) in vec4 Sweep;

layout(location = 0) out vec2 f_Position;
layout(location = 1) out vec4 f_StartColor;
layout(location = 2) out vec4 f_MiddleColor;
layout(location = 3) out vec4 f_EndColor;
layout(location = 4) out vec4 f_Circle;
layout(location = 5) out vec4 f_Sweep;

void main() {
#ifdef EPSILON_RETAINED
    epsilonTint = UiTint;
#endif
    gl_Position = ProjMat * ModelViewMat * epsilonPosition(vec4(Position, 1.0));

    f_Position = Position.xy;
    f_StartColor = StartColor;
    f_MiddleColor = MiddleColor;
    f_EndColor = EndColor;

    f_Circle = Circle;
    f_Sweep = Sweep;
}
