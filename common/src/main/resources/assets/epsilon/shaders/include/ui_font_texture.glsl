uniform sampler2D Sampler0;

#ifdef EPSILON_TEXTURE_TABLE
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;
uniform sampler2D Sampler6;
uniform sampler2D Sampler7;
layout(location = 6) flat in float epsilonTextureSlot;
#endif

vec3 epsilonFontSample(vec2 uv) {
#ifdef EPSILON_TEXTURE_TABLE
    // 显式梯度避免图集分支影响相邻片段的 LOD；常量 sampler 分支兼容 GLSL 4.10。
    vec2 dx = dFdx(uv);
    vec2 dy = dFdy(uv);
    switch (int(epsilonTextureSlot)) {
        case 1: return textureGrad(Sampler1, uv, dx, dy).rgb;
        case 2: return textureGrad(Sampler2, uv, dx, dy).rgb;
        case 3: return textureGrad(Sampler3, uv, dx, dy).rgb;
        case 4: return textureGrad(Sampler4, uv, dx, dy).rgb;
        case 5: return textureGrad(Sampler5, uv, dx, dy).rgb;
        case 6: return textureGrad(Sampler6, uv, dx, dy).rgb;
        case 7: return textureGrad(Sampler7, uv, dx, dy).rgb;
        default: return textureGrad(Sampler0, uv, dx, dy).rgb;
    }
#else
    return texture(Sampler0, uv).rgb;
#endif
}

// 与 MsdfGlyphGenerator.PIXEL_RANGE 保持一致；使用 RGB 中值恢复尖角处的距离。
const float EPSILON_MSDF_PIXEL_RANGE = 8.0;

float epsilonFontDistance(vec2 uv) {
    vec3 d = epsilonFontSample(uv);
    return max(min(d.r, d.g), min(max(d.r, d.g), d.b));
}

float epsilonFontScreenRange(vec2 uv) {
    vec2 unitRange = vec2(EPSILON_MSDF_PIXEL_RANGE) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = 1.0 / max(fwidth(uv), vec2(0.00000001));
    return max(0.5 * dot(unitRange, screenTexSize), 1.0);
}

float epsilonFontCoverage(vec2 uv, float screenRange) {
    // 单点 MSDF 重建保留一像素覆盖率过渡。
    return clamp((epsilonFontDistance(uv) - 0.5) * screenRange + 0.5, 0.0, 1.0);
}
