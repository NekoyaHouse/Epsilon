#version 410 core

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in vec4 GlyphUvBounds;

out vec4 v_Color;
out vec2 v_TexCoord;
flat out vec4 v_GlyphUvBounds;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    v_Color = Color;
    v_TexCoord = UV0;
    v_GlyphUvBounds = GlyphUvBounds;
}
