#version 410 core

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <epsilon:ui_properties.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec4 v_Color;
layout(location = 1) out vec3 v_Barycentric;

void main() {
#ifdef EPSILON_RETAINED
    epsilonTint = UiTint;
#endif
    gl_Position = ProjMat * ModelViewMat * epsilonPosition(vec4(Position, 1.0));
    v_Color = Color;

    int vertexId = gl_VertexIndex % 3;
    v_Barycentric = vec3(
        vertexId == 0 ? 1.0 : 0.0,
        vertexId == 1 ? 1.0 : 0.0,
        vertexId == 2 ? 1.0 : 0.0
    );
}
