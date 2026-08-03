#version 410 core

in vec2 v_TexCoord;
in vec4 v_Color;
flat in vec4 v_GlyphUvBounds;

uniform sampler2D Sampler0;

layout(std140) uniform GlitchData {
    vec4 Params0;
    vec4 Params1;
    vec4 Params2;
    vec4 Params3;
};

layout(location = 0) out vec4 f_Color;

float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

vec2 screenPixelOffset(vec2 pixels) {
    return dFdx(v_TexCoord) * pixels.x + dFdy(v_TexCoord) * pixels.y;
}

float glyphAlpha(vec2 uv) {
    vec2 lower = v_GlyphUvBounds.xy;
    vec2 upper = v_GlyphUvBounds.zw;
    if (uv.x < lower.x || uv.y < lower.y || uv.x > upper.x || uv.y > upper.y) {
        return 0.0;
    }
    return texture(Sampler0, uv).r;
}

void main() {
    float chromX = Params0.x;
    float chromY = Params0.y;
    float glowRadius = max(Params0.z, 0.0);
    float glowIntensity = Params0.w;

    float sliceHeight = max(Params1.x, 1.0);
    float sliceAmount = Params1.y;
    float glitch = clamp(Params1.z, 0.0, 1.0);
    float scanlineStrength = Params1.w;

    vec3 neonColor = clamp(Params2.rgb, 0.0, 3.0);
    float noiseStrength = Params2.w;
    float time = Params3.x;

    float frame = floor(time * 60.0);
    float band = floor(gl_FragCoord.y / sliceHeight);
    float seed1 = rand(vec2(band, frame));
    float seed2 = rand(vec2(band + 17.0, frame + 11.0));
    float shiftPx = (seed1 - 0.5) * 2.0 * sliceAmount * glitch;
    float jumpPx = (seed2 - 0.5) * 2.0 * sliceAmount * 0.18 * glitch;
    vec2 uv = v_TexCoord + screenPixelOffset(vec2(shiftPx, jumpPx));

    vec2 chromaticOffset = screenPixelOffset(vec2(chromX, chromY) * (0.75 + 0.25 * glitch));
    float centerAlpha = glyphAlpha(uv);
    float redAlpha = glyphAlpha(uv + chromaticOffset);
    float greenAlpha = centerAlpha;
    float blueAlpha = glyphAlpha(uv - chromaticOffset);
    vec3 core = neonColor * vec3(redAlpha, greenAlpha, blueAlpha);

    vec2 radiusX = screenPixelOffset(vec2(glowRadius, 0.0));
    vec2 radiusY = screenPixelOffset(vec2(0.0, glowRadius));
    vec2 radiusDiagonal = screenPixelOffset(vec2(glowRadius * 0.7071));
    vec2 radiusCross = screenPixelOffset(vec2(glowRadius * 0.7071, -glowRadius * 0.7071));
    float glowAlpha = 0.0;
    glowAlpha += glyphAlpha(uv + radiusX);
    glowAlpha += glyphAlpha(uv - radiusX);
    glowAlpha += glyphAlpha(uv + radiusY);
    glowAlpha += glyphAlpha(uv - radiusY);
    glowAlpha += glyphAlpha(uv + radiusDiagonal);
    glowAlpha += glyphAlpha(uv - radiusDiagonal);
    glowAlpha += glyphAlpha(uv + radiusCross);
    glowAlpha += glyphAlpha(uv - radiusCross);
    glowAlpha = pow(clamp(glowAlpha * 0.125, 0.0, 1.0), 1.15) * glowIntensity;

    float glowRed = glyphAlpha(uv + chromaticOffset * 1.5);
    float glowGreen = centerAlpha;
    float glowBlue = glyphAlpha(uv - chromaticOffset * 1.5);
    vec3 glow = neonColor * vec3(glowRed, glowGreen, glowBlue) * glowAlpha;

    float scanline = 1.0 - scanlineStrength * 0.10 * (0.5 + 0.5 * sin(gl_FragCoord.y * 2.4 + time * 24.0));
    float flicker = 0.92 + 0.08 * rand(vec2(frame, band));
    float noise = (rand(floor(gl_FragCoord.xy) + vec2(frame, frame * 1.37)) - 0.5) * noiseStrength * 0.06;

    float coverage = clamp(max(max(redAlpha, max(greenAlpha, blueAlpha)), glowAlpha * 0.65), 0.0, 1.0);
    float alpha = coverage * v_Color.a;
    if (alpha < 0.001) {
        discard;
    }

    vec3 rgb = (core + glow) * scanline * flicker * v_Color.rgb;
    rgb += vec3(max(noise, 0.0)) * coverage;
    f_Color = vec4(max(rgb, 0.0), alpha);
}
