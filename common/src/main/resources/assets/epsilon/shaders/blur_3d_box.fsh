#version 410 core

uniform sampler2D InputSampler;

layout(std140) uniform BoxBlurUniforms {
    vec4 Params;
};

layout(location = 0) out vec4 fragColor;

const float TAU = 6.28318530718;
const int DIRECTION_COUNT = 16;
const int RING_COUNT = 5;
const float SAMPLE_COUNT = float(1 + DIRECTION_COUNT * RING_COUNT);

void main() {
    vec2 inputResolution = Params.xy;
    float quality = max(0.0, Params.z);
    vec2 uv = gl_FragCoord.xy / inputResolution;

    vec4 centerSample = texture(InputSampler, uv);
    vec3 centerColor = centerSample.rgb;

    if (quality <= 0.0) {
        fragColor = vec4(centerColor, 1.0);
        return;
    }

    vec2 radius = quality / inputResolution;
    vec3 colorSum = centerColor;

    for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
        float angle = TAU * float(direction) / float(DIRECTION_COUNT);
        vec2 directionOffset = vec2(cos(angle), sin(angle)) * radius;

        for (int ring = 1; ring <= RING_COUNT; ring++) {
            float ringOffset = float(ring) / float(RING_COUNT);
            vec4 sampleColor = texture(InputSampler, uv + directionOffset * ringOffset);
            float signal = max(sampleColor.a, max(sampleColor.r, max(sampleColor.g, sampleColor.b)));
            colorSum += signal > 1e-5 ? sampleColor.rgb : centerColor;
        }
    }

    fragColor = vec4(colorSum / SAMPLE_COUNT, 1.0);
}
