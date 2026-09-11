#version 410 core

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 StartColor;
layout(location = 2) in vec4 MiddleColor;
layout(location = 3) in vec4 EndColor;
layout(location = 4) in vec4 Circle;
layout(location = 5) in vec4 Sweep;

out vec2 f_Position;
out vec4 f_StartColor;
out vec4 f_MiddleColor;
out vec4 f_EndColor;
out vec4 f_Circle;
out vec4 f_Sweep;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    f_Position = Position.xy;
    f_StartColor = StartColor;
    f_MiddleColor = MiddleColor;
    f_EndColor = EndColor;

    f_Circle = Circle;
    f_Sweep = Sweep;
}
