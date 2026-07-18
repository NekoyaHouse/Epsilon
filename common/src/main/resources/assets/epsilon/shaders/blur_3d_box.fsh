#version 410 core

uniform sampler2D InputSampler;

layout(std140) uniform BoxBlurUniforms {
    vec4 Params;
};

layout(location = 0) out vec4 fragColor;

vec4 blur() {
    #define TAU 6.28318530718

    vec2 inputResolution = Params.xy;
    float quality = Params.z;
    vec2 radius = quality / inputResolution.xy;
    vec2 uv = gl_FragCoord.xy / inputResolution.xy;

    vec4 color = texture(InputSampler, uv);
    float step = TAU / 16.0;

    for (float d = 0.0; d < TAU; d += step) {
        for (float i = 0.2; i <= 1.0; i += 0.2) {
            color += texture(InputSampler, uv + vec2(cos(d), sin(d)) * radius * i);
        }
    }

    return color / 81.0;
}

void main() {
    fragColor = vec4(blur().rgb, 1.0);
}
