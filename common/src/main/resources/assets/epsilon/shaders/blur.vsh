#version 410 core

out vec2 vUv;

void main() {
    vec2 pos;
    if (gl_VertexID == 0) pos = vec2(-1.0, -1.0);
    else if (gl_VertexID == 1) pos = vec2(3.0, -1.0);
    else pos = vec2(-1.0, 3.0);
    vUv = pos * 0.5 + 0.5;
    gl_Position = vec4(pos, 0.0, 1.0);
}
