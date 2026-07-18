#version 410 core

layout(location = 0) in vec4 v_Color;

out vec4 f_Color;

void main() {
    f_Color = v_Color;
}