#version 410 core

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;

layout(location = 0) out vec4 v_Color;
layout(location = 1) out vec3 v_Barycentric;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    v_Color = Color;

    int vertexId = gl_VertexID % 3;
    v_Barycentric = vec3(
        vertexId == 0 ? 1.0 : 0.0,
        vertexId == 1 ? 1.0 : 0.0,
        vertexId == 2 ? 1.0 : 0.0
    );
}
