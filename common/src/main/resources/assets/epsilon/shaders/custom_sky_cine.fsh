#version 410 core

layout(std140) uniform CustomSky {
    vec4 SkyColor;
    vec4 BackgroundColor;
    vec4 CustomSkyInfo;
};

#define u_Color SkyColor
#define u_Resolution CustomSkyInfo.xy
#define u_Mouse vec2(0.0)
#define u_Scale 1.0
#define u_Time CustomSkyInfo.z
#define mixFactor SkyColor.a

layout(location = 0) out vec4 fragColor;

#define SS(x, y, z) smoothstep(x, y, z)
#define MD(a) mat2(cos(a), -sin(a), sin(a), cos(a))

const float divx = 35.0;
#define polar_line_scale (2.0 / divx)
const float zoom_nise = 9.0;

mat3 rotx(float a) {
    float s = sin(a);
    float c = cos(a);
    return mat3(vec3(1.0, 0.0, 0.0), vec3(0.0, c, s), vec3(0.0, -s, c));
}

mat3 roty(float a) {
    float s = sin(a);
    float c = cos(a);
    return mat3(vec3(c, 0.0, s), vec3(0.0, 1.0, 0.0), vec3(-s, 0.0, c));
}

mat3 rotz(float a) {
    float s = sin(a);
    float c = cos(a);
    return mat3(vec3(c, s, 0.0), vec3(-s, c, 0.0), vec3(0.0, 0.0, 1.0));
}

float linearstep(float begin, float end, float t) {
    return clamp((t - begin) / (end - begin), 0.0, 1.0);
}

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return -1.0 + 2.0 * fract((p3.x + p3.y) * p3.z);
}

float noise(in vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i + vec2(0.0, 0.0)), hash(i + vec2(1.0, 0.0)), u.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x),
        u.y
    );
}

float fbm(in vec2 p) {
    p *= 0.25;
    float s = 0.5;
    float f = 0.0;
    for (int i = 0; i < 4; i++) {
        f += s * noise(p);
        s *= 0.8;
        p = 2.01 * mat2(0.8, 0.6, -0.6, 0.8) * p;
    }
    return 0.5 + 0.5 * f;
}

vec2 ToPolar(vec2 v) {
    return vec2(atan(v.y, v.x) / 3.1415926, length(v));
}

float angleShift(vec2 p, float timer) {
    float invLen = inversesqrt(max(dot(p, p), 1e-6));
    vec2 n = p * invLen;
    float a = (n.x * 0.70710678 + n.y * 0.70710678);
    return 0.5 * a + fract(timer * 0.025);
}

vec3 fcos(vec3 x) {
    vec3 w = fwidth(x);
    return cos(x) * smoothstep(3.14 * 2.0, 0.0, w);
}

vec3 getColor(in float t) {
    vec3 col = vec3(0.3, 0.4, 0.5);
    col += 0.12 * fcos(6.28318 * t * 1.0 + vec3(0.0, 0.8, 1.1));
    col += 0.11 * fcos(6.28318 * t * 3.1 + vec3(0.3, 0.4, 0.1));
    col += 0.10 * fcos(6.28318 * t * 5.1 + vec3(0.1, 0.7, 1.1));
    col += 0.10 * fcos(6.28318 * t * 17.1 + vec3(0.2, 0.6, 0.7));
    col += 0.10 * fcos(6.28318 * t * 31.1 + vec3(0.1, 0.6, 0.7));
    col += 0.10 * fcos(6.28318 * t * 65.1 + vec3(0.0, 0.5, 0.8));
    col += 0.10 * fcos(6.28318 * t * 115.1 + vec3(0.1, 0.4, 0.7));
    col += 0.10 * fcos(6.28318 * t * 265.1 + vec3(1.1, 1.4, 2.7));
    return col;
}

vec3 pal(in float t, in vec3 a, in vec3 b, in vec3 c, in vec3 d) {
    return a + b * cos(6.28318 * (c * t + d));
}

vec3 get_noise(vec2 p, float timer) {
    vec2 res = u_Resolution.xy / u_Resolution.y;
    vec2 shiftx = res * 0.5 * 1.25 + 0.5 * (0.5 + 0.5 * vec2(sin(timer * 0.0851), cos(timer * 0.0851)));
    vec2 shiftx2 = res * 0.5 * 2.0 + 0.5 * (0.5 + 0.5 * vec2(sin(timer * 0.0851), cos(timer * 0.0851)));
    vec2 tp = p + shiftx;
    float atx = angleShift(tp, timer);
    vec2 puv = ToPolar(tp);
    puv.y += atx;
    puv.x *= 0.5;
    vec2 tuv = puv * divx;
    float idx = mod(floor(tuv.y), divx) + 200.0;
    puv.y = fract(puv.y);
    puv.x = abs(fract(puv.x / divx) - 0.5) * divx;
    puv.x += -0.5 * timer * (0.075 - 0.0025 * max((min(idx, 16.0) + 2.0 * sin(idx / 5.0)), 0.0));
    return vec3(
        SS(0.43, 0.73, fbm(((p * 0.5 + shiftx2) * MD(-timer * 0.013951 * 10.0 / zoom_nise)) * zoom_nise * 2.0 + vec2(4.0 + 2.0 * idx))),
        SS(0.543, 0.73, fbm(((p * 0.5 + shiftx2) * MD(timer * 0.02751 * 10.0 / zoom_nise)) * zoom_nise * 1.4 + vec2(4.0 + 2.0 * idx))),
        fbm(vec2(4.0 + 2.0 * idx) * puv * zoom_nise / 100.0)
    );
}

vec4 get_lines_color(vec2 p, vec3 n, float timer) {
    vec2 res = u_Resolution.xy / u_Resolution.y;
    vec3 col = vec3(0.0);
    float a = 1.0;
    vec2 shiftx = res * 0.5 * 1.25 + 0.5 * (0.5 + 0.5 * vec2(sin(timer * 0.0851), cos(timer * 0.0851)));
    vec2 tp = p + shiftx;
    float atx = angleShift(tp, timer);
    vec2 puv = ToPolar(tp);
    puv.y += atx;
    puv.x *= 0.5;
    vec2 tuv = puv * divx;
    float idx = mod(floor(tuv.y), divx) + 1.0;
    float d = length(tp);
    d += atx;
    float v = sin(3.141592653 * 2.0 * divx * 0.5 * d + 0.5 * 3.141592653);
    float fv = fwidth(v);
    fv += 0.0001 * (1.0 - abs(sign(fv)));
    d = 1.0 - SS(-1.0, 1.0, 0.3 * abs(v) / fv);
    float d2 = 1.0 - SS(0.0, 0.473, abs(fract(tuv.y) - 0.5));
    tuv.x += 3.5 * timer * (0.01 + divx / 200.0) - 0.435 * idx;
    tuv.x = abs(fract(tuv.x / divx) - 0.5) * divx;
    float ld = SS(0.1, 0.9, (fract(polar_line_scale * tuv.x * max(idx, 1.0) / 10.0 + idx / 3.0))) *
               (1.0 - SS(0.98, 1.0, (fract(polar_line_scale * tuv.x * max(idx, 1.0) / 10.0 + idx / 3.0))));
    tuv.x += 1.0 * timer * (0.01 + divx / 200.0) - 1.135 * idx;
    ld *= 1.0 - SS(0.1, 0.9, (fract(polar_line_scale * tuv.x * max(idx, 1.0) / 10.0 + idx / 6.5))) *
                 (1.0 - SS(0.98, 1.0, (fract(polar_line_scale * tuv.x * max(idx, 1.0) / 10.0 + idx / 6.5))));
    float ld2 = 0.1 / (max(abs(fract(tuv.y) - 0.5) * 1.46, 0.0001) + ld);
    ld = 0.1 / ((max(abs(fract(tuv.y) - 0.5) * 1.46, 0.0001) + ld) * (2.5 - (n.y + 1.0 * max(n.y, n.z))));
    ld = min(ld, 13.0);
    ld *= SS(0.0, 0.15, 0.5 - abs(fract(tuv.y) - 0.5));
    d *= n.z * n.z * 2.0;
    float d3 = (d * n.x * n.y + d * n.y * n.y + (d2 * ld2 + d2 * ld * n.z * n.z));
    d = (d * n.x * n.y + d * n.y * n.y + (d2 * ld + d2 * ld * n.z * n.z));
    a = clamp(d, 0.0, 1.0);
    puv.y = mix(fract(puv.y), fract(puv.y + 0.5), SS(0.0, 0.1, abs(fract(puv.y) - 0.5)));
    col = getColor(0.54 * length(puv.y));
    col = 3.5 * a * col * col + 2.0 * (mix(col.bgr, col.grb, 0.5 + 0.5 * sin(timer * 0.1)) - col * 0.5) * col;
    d3 = min(d3, 4.0);
    d3 *= (d3 * n.y - (n.y * n.x * n.z));
    d3 *= n.y / max(n.z + n.x, 0.001);
    d3 = max(d3, 0.0);
    vec3 col2 = 0.5 * d3 * vec3(0.3, 0.7, 0.98);
    col2 = clamp(col2, 0.0, 2.0);
    col = col2 * 0.5 * (0.5 - 0.5 * cos((timer * 0.48 * 2.0))) + mix(col, col2, 0.45 + 0.45 * cos((timer * 0.48 * 2.0)));
    col = clamp(col, 0.0, 1.0);
    return vec4(col, a);
}

vec4 planet(vec3 ro, vec3 rd, float timer, out float cineshader_alpha) {
    vec3 lgt = vec3(-0.523, 0.41, -0.747);
    float sd = clamp(dot(lgt, rd) * 0.5 + 0.5, 0.0, 1.0);
    float far = 400.0;
    float dtp = 13.0 - (ro + rd * far).y * 3.5;
    float hori = (linearstep(-1900.0, 0.0, dtp) - linearstep(11.0, 700.0, dtp)) * 1.0;
    hori *= pow(abs(sd), 0.04);
    hori = abs(hori);
    vec3 col = vec3(0.0);
    col += pow(hori, 200.0) * vec3(0.3, 0.7, 1.0) * 3.0;
    col += pow(hori, 25.0) * vec3(0.5, 0.5, 1.0) * 0.5;
    col += pow(hori, 7.0) * pal(timer * 0.48 * 0.1, vec3(0.8, 0.5, 0.04), vec3(0.3, 0.04, 0.82), vec3(2.0, 1.0, 1.0), vec3(0.0, 0.25, 0.25)) * 1.0;
    col = clamp(col, 0.0, 1.0);
    float t = mod(timer, 15.0);
    float t2 = mod(timer + 7.5, 15.0);
    float td = 0.071 * dtp / far + 5.1;
    float td2 = 0.1051 * dtp / far + t * 0.00715 + 0.025;
    float td3 = 0.1051 * dtp / far + t2 * 0.00715 + 0.025;
    vec3 c1 = getColor(td);
    vec3 c2 = getColor(td2);
    vec3 c3 = getColor(td3);
    c2 = mix(c2, c3.bbr, abs(t - 7.5) / 7.5);
    c2 = clamp(c2, 0.0001, 1.0);
    col += sd * hori * clamp((c1 / (2.0 * c2)), 0.0, 3.0) * SS(0.0, 50.0, dtp);
    col = clamp(col, 0.0, 1.0);
    float a = (0.15 + 0.95 * (1.0 - sd)) * hori * (1.0 - SS(0.0, 25.0, dtp));
    a = clamp(a, 0.0, 1.0);
    hori = mix(linearstep(-1900.0, 0.0, dtp), 1.0 - linearstep(11.0, 700.0, dtp), sd);
    cineshader_alpha = 1.0 - pow(hori, 3.5);
    return vec4(col, a);
}

vec3 cam(vec2 uv, float timer) {
    timer *= 0.48;
    vec2 im = vec2(cos(mod(timer, 3.1415926)), -0.02 + 0.06 * cos(timer * 0.17));
    im *= 3.14159263;
    im.y = -im.y;
    float fov = 90.0;
    float aspect = 1.0;
    float screenSize = (1.0 / tan(((180.0 - fov) * (3.14159263 / 180.0)) / 2.0));
    vec3 rd = normalize(vec3(uv * screenSize, 1.0 / aspect));
    rd = (roty(-im.x) * rotx(im.y) * rotz(0.32 * sin(timer * 0.07))) * rd;
    return rd;
}

const mat3 ACESInputMat = mat3(
    0.59719, 0.35458, 0.04823,
    0.07600, 0.90834, 0.01566,
    0.02840, 0.13383, 0.83777
);

const mat3 ACESOutputMat = mat3(
    1.60475, -0.53108, -0.07367,
    -0.10208,  1.10813, -0.00605,
    -0.00327, -0.07276,  1.07602
);

vec3 RRTAndODTFit(vec3 v) {
    vec3 a = v * (v + 0.0245786) - 0.000090537;
    vec3 b = v * (0.983729 * v + 0.4329510) + 0.238081;
    return a / b;
}

vec3 ACESFitted(vec3 color) {
    color = color * ACESInputMat;
    color = RRTAndODTFit(color);
    color = color * ACESOutputMat;
    return clamp(color, 0.0, 1.0);
}

void main() {
    float timer = 0.65 * u_Time + 220.0;
    vec2 res = u_Resolution.xy / u_Resolution.y;
    vec2 uv = gl_FragCoord.xy / u_Resolution.y - 0.5 * res;

    uv *= 2.5 * max(u_Scale, 0.0001);

    vec3 noisev = get_noise(uv, timer);
    vec4 lcol = get_lines_color(uv, noisev, timer);

    vec3 ro = vec3(1.0, 40.0, 1.0);
    vec3 rd = cam(uv, timer);

    float cineAlpha;
    vec4 planetc = planet(ro, rd, timer, cineAlpha);

    vec3 col = lcol.rgb * planetc.a * 0.75 + 0.5 * lcol.rgb * min(12.0 * planetc.a, 1.0) + planetc.rgb;
    col = clamp(col, 0.0, 1.0);

    vec3 overlay = vec3(col * 0.85 + 0.15 * col * col);
    overlay = overlay * 0.15 + overlay * overlay * 0.65 + (overlay * 0.7 + 0.3) * ACESFitted(overlay);
    overlay = clamp(overlay, 0.0, 1.0);
    overlay = clamp(overlay * clamp(u_Color.rgb, 0.0, 1.0), 0.0, 1.0);

    float m = clamp(mixFactor, 0.0, 1.0);
    fragColor = vec4(overlay * m, 1.0);
}
