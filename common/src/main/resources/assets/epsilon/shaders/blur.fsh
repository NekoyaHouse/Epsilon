#version 410 core

uniform sampler2D InputSampler;

layout(std140) uniform BlurUniforms {
    vec3 InputInfo;
    vec4 Rect;
    vec4 CornerRadii;
};

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

vec4 blur() {
    #define TAU 6.28318530718

    vec2 inputResolution = InputInfo.xy;
    float quality = InputInfo.z;
    vec2 radius = quality / inputResolution.xy;

    vec2 uv = gl_FragCoord.xy / inputResolution.xy;
    vec4 color = texture(InputSampler, uv);

    float step =  TAU / 16.0;

    for (float d = 0.0; d < TAU; d += step) {
        for (float i = 0.2; i <= 1.0; i += 0.2) {
            color += texture(InputSampler, uv + vec2(cos(d), sin(d)) * radius * i);
        }
    }

    color /= 81.0;
    return color;
}

void main() {
    vec2 uSize = Rect.xy;
    vec2 uLocation = Rect.zw;
    vec4 radii = CornerRadii;
    vec4 bounds = vec4(uLocation, uLocation + uSize);

    float dist = roundRectDistance(gl_FragCoord.xy, bounds, radii);
    float delta = fwidth(dist);
    float alpha = 1.0 - smoothstep(-delta, delta, dist);

    fragColor = vec4(blur().rgb, alpha);
    if (alpha < 0.001) discard;
}
