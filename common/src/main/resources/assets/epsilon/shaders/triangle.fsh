#version 410 core

layout(location = 0) in vec4 v_Color;
layout(location = 1) in vec3 v_Barycentric;

out vec4 f_Color;

void main() {
    float d = min(min(v_Barycentric.x, v_Barycentric.y), v_Barycentric.z);
    float w = fwidth(d);
    float alpha = smoothstep(0.0, w, d);
    f_Color = vec4(v_Color.rgb, v_Color.a * alpha);
}
