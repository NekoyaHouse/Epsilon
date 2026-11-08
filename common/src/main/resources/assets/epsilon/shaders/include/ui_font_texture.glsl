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

float epsilonFontDistance(vec2 uv) {
#ifdef EPSILON_TEXTURE_TABLE
    // 显式梯度避免图集分支影响相邻片段的 LOD；常量 sampler 分支兼容 GLSL 4.10。
    vec2 dx = dFdx(uv);
    vec2 dy = dFdy(uv);
    switch (int(epsilonTextureSlot)) {
        case 1: return textureGrad(Sampler1, uv, dx, dy).r;
        case 2: return textureGrad(Sampler2, uv, dx, dy).r;
        case 3: return textureGrad(Sampler3, uv, dx, dy).r;
        case 4: return textureGrad(Sampler4, uv, dx, dy).r;
        case 5: return textureGrad(Sampler5, uv, dx, dy).r;
        case 6: return textureGrad(Sampler6, uv, dx, dy).r;
        case 7: return textureGrad(Sampler7, uv, dx, dy).r;
        default: return textureGrad(Sampler0, uv, dx, dy).r;
    }
#else
    return texture(Sampler0, uv).r;
#endif
}
