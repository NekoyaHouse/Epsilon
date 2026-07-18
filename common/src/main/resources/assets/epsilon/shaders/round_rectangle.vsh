#version 410 core

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec4 InnerRect;
layout(location = 3) in vec4 Radius;

out vec2 f_Position;
out vec4 f_Color;
out vec4 f_InnerRect;
out vec4 f_Radius;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    f_Position = Position.xy;
    f_Color = Color;

    f_InnerRect = InnerRect;
    f_Radius = Radius;
}
