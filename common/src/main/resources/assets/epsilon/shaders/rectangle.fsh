#version 410 core

layout(location = 0) in vec4 v_Color;

layout(location = 0) out vec4 f_Color;

#ifdef EPSILON_RETAINED
layout(location = 7) flat in vec4 epsilonTint;
#endif

void main() {
    f_Color = v_Color;
#ifdef EPSILON_RETAINED
    f_Color *= epsilonTint;
#endif
}
