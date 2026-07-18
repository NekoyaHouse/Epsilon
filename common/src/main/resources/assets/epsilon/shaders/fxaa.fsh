#version 410 core

uniform sampler2D InputSampler;

layout(std140) uniform FxaaInfo {
    vec4 ScreenSize;
};

in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

#ifndef FXAA_REDUCE_MIN
    #define FXAA_REDUCE_MIN (1.0 / 128.0)
#endif

#ifndef FXAA_REDUCE_MUL
    #define FXAA_REDUCE_MUL (1.0 / 8.0)
#endif

#ifndef FXAA_SPAN_MAX
    #define FXAA_SPAN_MAX 8.0
#endif

float luma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 inverseVP = ScreenSize.zw;

    vec3 rgbNW = texture(InputSampler, texCoord + vec2(-1.0, -1.0) * inverseVP).rgb;
    vec3 rgbNE = texture(InputSampler, texCoord + vec2(1.0, -1.0) * inverseVP).rgb;
    vec3 rgbSW = texture(InputSampler, texCoord + vec2(-1.0, 1.0) * inverseVP).rgb;
    vec3 rgbSE = texture(InputSampler, texCoord + vec2(1.0, 1.0) * inverseVP).rgb;
    vec4 texColor = texture(InputSampler, texCoord);
    vec3 rgbM = texColor.rgb;

    float lumaNW = luma(rgbNW);
    float lumaNE = luma(rgbNE);
    float lumaSW = luma(rgbSW);
    float lumaSE = luma(rgbSE);
    float lumaM = luma(rgbM);

    float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
    float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));

    vec2 dir;
    dir.x = -((lumaNW + lumaNE) - (lumaSW + lumaSE));
    dir.y = (lumaNW + lumaSW) - (lumaNE + lumaSE);

    float dirReduce = max(
        (lumaNW + lumaNE + lumaSW + lumaSE) * (0.25 * FXAA_REDUCE_MUL),
        FXAA_REDUCE_MIN
    );

    float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
    dir = clamp(dir * rcpDirMin, vec2(-FXAA_SPAN_MAX), vec2(FXAA_SPAN_MAX)) * inverseVP;

    vec3 rgbA = 0.5 * (
        texture(InputSampler, texCoord + dir * (1.0 / 3.0 - 0.5)).rgb +
        texture(InputSampler, texCoord + dir * (2.0 / 3.0 - 0.5)).rgb
    );

    vec3 rgbB = rgbA * 0.5 + 0.25 * (
        texture(InputSampler, texCoord + dir * -0.5).rgb +
        texture(InputSampler, texCoord + dir * 0.5).rgb
    );

    float lumaB = luma(rgbB);
    if (lumaB < lumaMin || lumaB > lumaMax) {
        fragColor = vec4(rgbA, texColor.a);
    } else {
        fragColor = vec4(rgbB, texColor.a);
    }
}
