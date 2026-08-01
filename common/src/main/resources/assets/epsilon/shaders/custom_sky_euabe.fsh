#version 410 core

layout(std140) uniform CustomSky {
    vec4 SkyColor;
    vec4 BackgroundColor;
    vec4 CustomSkyInfo;
};

#define u_Resolution CustomSkyInfo.xy
#define u_Time CustomSkyInfo.z
#define mixFactor SkyColor.a

layout(location = 0) out vec4 fragColor;

#define N(x,r) abs(dot(sin(x*p*s), r + p - p)) / s

float streak(vec2 uv, float t) {
    uv += t * 0.1;
    uv.x += sin(uv.y * 4.0 + t) * 0.15;
    float s = smoothstep(0.02, 0.0, abs(fract(uv.x * 20.0) - 0.5));
    return s * 0.5;
}

float shimmer(vec2 uv, float t) {
    uv += t * 0.08;
    float n = sin((uv.x + uv.y) * 15.0 + t * 2.0) * 0.5 + 0.5;
    return smoothstep(0.85, 1.0, n) * 0.15;
}

float cloudTwinkle(vec2 uv, float t, vec2 sunPos) {
    uv.x -= sunPos.x * 0.5;
    uv.y += sin(t * 0.4 + uv.x * 3.0) * 0.02;
    float n = sin((uv.x * 10.0 + uv.y * 12.0) + t * 5.0) * 0.5 + 0.5;
    n = pow(n, 8.0);
    return n * 0.25;
}

float horizonFade(vec2 uv) {
    return smoothstep(-0.4, -0.05, uv.y);
}

void main() {
    vec2 uv = gl_FragCoord.xy / u_Resolution.y - 0.5 * (u_Resolution.xy / u_Resolution.y);
    float t = u_Time;
    vec3 o = vec3(1.0);
    vec3 p, q;
    mat2 m;
    float i = 0.0, d = 0.0, s = 0.0;

    for (i = 0.0; i < 100.0; i += 1.0) {
        q = p = vec3(uv * d, d + t * 6.0) / 2.0;

        float angle = cos(t * 0.6) * 0.12;
        m = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
        q.xy *= m;
        p.xy *= m;

        p.y += sin(t * 0.3);

        for (s = 0.03; s < 2.0; s += s) {
            p += N(16.0, 0.012);
            q += N(t + q + 24.0, 0.002);
        }

        float dtmp = 0.04 + min(
            0.2 * abs(3.0 - q - cos(p.x) * 0.2) + 0.001,
            0.9 * abs(1.0 + p) + 0.005
        ).y;

        d += s = dtmp;
        o += 1.0 / s;
    }

    uv -= vec2(0.3, 0.2);

    float st = streak(uv, t);
    o += vec3(st);

    float sh = shimmer(uv, t);
    o += vec3(sh);

    vec2 sunPos = vec2(0.23 + 0.7 * fract(t * 0.05), 0.83);
    float sunDist = length(uv - sunPos);
    float sunGlow = clamp(1.0 - sunDist / 0.38, 0.0, 1.0);
    vec3 sunCol = vec3(1.0, 0.9, 0.7);
    o += sunCol * sunGlow * 0.85;

    float tw = cloudTwinkle(uv, t, sunPos);
    o += vec3(tw);

    vec3 topColor = vec3(1.0, 0.35, 0.6);
    vec3 bottomColor = vec3(3.8, 3.2, 4.5);
    float f = smoothstep(0.2, -0.5, uv.y);
    o *= mix(pow(topColor, vec3(1.6)), bottomColor, f);

    float hf = horizonFade(uv);
    o *= hf + (1.0 - hf) * 0.35;

    o = tanh(o * o / 3e6 / max(length(uv), 1e-4));
    o = clamp(o * 1.1, 0.0, 1.0);

    fragColor = vec4(o * clamp(mixFactor, 0.0, 1.0), 1.0);
}
