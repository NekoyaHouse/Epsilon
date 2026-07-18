#version 410 core

in vec4 v_Color;
in vec2 v_TexCoord;

uniform sampler2D Sampler0;

layout(location = 0) out vec4 f_Color;

const float EDGE_THRESHOLD = 0.5;

void main() {
    float distance = 1.0 - texture(Sampler0, v_TexCoord).r;
    float alpha = step(EDGE_THRESHOLD, distance);

    f_Color = vec4(v_Color.rgb, v_Color.a * alpha);

    if (f_Color.a < 0.005) {
        discard;
    }
}
