#version 410 core

in vec2 f_Position;
in vec4 f_Color;
in vec4 f_InnerRect;
in vec4 f_Radius;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 halfSize = (f_InnerRect.zw - f_InnerRect.xy) * 0.5;
    vec2 center = (f_InnerRect.xy + f_InnerRect.zw) * 0.5;
    vec2 p = f_Position - center;

    vec2 s = step(0.0, p);

    float r_current = mix(
        mix(f_Radius.x, f_Radius.w, s.y),
        mix(f_Radius.y, f_Radius.z, s.y),
        s.x
    );

    vec2 q = abs(p) - halfSize + r_current;
    float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r_current;

    float delta = fwidth(dist);
    float alpha = 1.0 - smoothstep(-delta, delta, dist);

    fragColor = vec4(f_Color.rgb, f_Color.a * alpha);
    if (alpha < 0.001) discard;
}
