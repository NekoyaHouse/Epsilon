#version 410 core

layout(location = 0) in vec2 f_Position;
layout(location = 1) in vec4 f_Color;
layout(location = 2) in vec2 f_TexCoord;
layout(location = 3) in vec4 f_InnerRect;
layout(location = 4) in vec4 f_Radius;

layout(location = 0) out vec4 fragColor;

uniform sampler2D Sampler0;

float aastep(float x) {
    float afwidth = fwidth(x);
    return smoothstep(-afwidth, afwidth, x);
}

#ifdef EPSILON_RETAINED
layout(location = 7) flat in vec4 epsilonTint;
#define main epsilonOriginalMain
#endif

void main() {
    vec4 tex = texture(Sampler0, f_TexCoord);
    vec4 color = tex * f_Color;

    vec2 halfSize = (f_InnerRect.zw - f_InnerRect.xy) * 0.5;
    vec2 center = (f_InnerRect.xy + f_InnerRect.zw) * 0.5;
    vec2 p = f_Position - center;

    float r_current = (p.x > 0.0) ? 
        ((p.y > 0.0) ? f_Radius.z : f_Radius.y) : 
        ((p.y > 0.0) ? f_Radius.w : f_Radius.x);

    vec2 q = abs(p) - halfSize + r_current;
    float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r_current;

    float delta = fwidth(dist);
    float alpha = 1.0 - smoothstep(-delta, delta, dist);

    if (alpha < 0.001 || color.a < 0.01) discard;
#ifdef EPSILON_PREMULTIPLIED_ALPHA
    fragColor = vec4(color.rgb * f_Color.a * alpha, color.a * alpha);
#else
    fragColor = vec4(color.rgb, color.a * alpha);
#endif
}

#ifdef EPSILON_RETAINED
#undef main
void main() {
    epsilonOriginalMain();
#ifdef EPSILON_PREMULTIPLIED_ALPHA
    fragColor *= vec4(epsilonTint.rgb * epsilonTint.a, epsilonTint.a);
#else
    fragColor *= epsilonTint;
#endif
}
#endif
