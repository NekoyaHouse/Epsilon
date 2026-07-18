#version 410 core

in vec2 f_Position;
in vec4 f_Color;
in vec4 f_InnerRect;
in vec4 f_Radius;
in float f_OutlineWidth;

layout(location = 0) out vec4 fragColor;

float roundRectDistance(vec2 position, vec4 innerRect, vec4 radius) {
    vec2 halfSize = (innerRect.zw - innerRect.xy) * 0.5;
    vec2 center = (innerRect.xy + innerRect.zw) * 0.5;
    vec2 p = position - center;

    vec2 s = step(0.0, p);
    float rCurrent = mix(
        mix(radius.x, radius.w, s.y),
        mix(radius.y, radius.z, s.y),
        s.x
    );

    vec2 q = abs(p) - halfSize + rCurrent;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rCurrent;
}

void main() {
    float dist = roundRectDistance(f_Position, f_InnerRect, f_Radius);
    float halfOutline = max(f_OutlineWidth * 0.5, 0.0);
    float delta = max(fwidth(dist), 0.0001);
    float alpha = 1.0 - smoothstep(halfOutline - delta, halfOutline + delta, abs(dist));

    fragColor = vec4(f_Color.rgb, f_Color.a * alpha);
    if (fragColor.a < 0.001) discard;
}
