#version 410 core

layout(std140) uniform GlslSandboxInfo {
    vec4 SandboxResolutionTime;
    vec4 SandboxMouse;
};

layout(location = 0) out vec4 fragColor;

#define ITR 90
#define FAR 400.0
#define iResolution SandboxResolutionTime.xy
#define iTime SandboxResolutionTime.z
#define iMouse SandboxMouse.zw
#define time iTime

const vec3 lgt = vec3(-0.523, 0.41, -0.747);
const mat2 m2 = mat2(0.80, 0.60, -0.60, 0.80);

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec3 noised(in vec2 x) {
    vec2 p = floor(x);
    vec2 f = fract(x);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(p + vec2(0.0, 0.0));
    float b = hash21(p + vec2(1.0, 0.0));
    float c = hash21(p + vec2(0.0, 1.0));
    float d = hash21(p + vec2(1.0, 1.0));
    return vec3(
            a + (b - a) * u.x + (c - a) * u.y + (a - b - c + d) * u.x * u.y,
            6.0 * f * (1.0 - f) * (vec2(b - a, c - a) + (a - b - c + d) * u.yx)
    );
}

float terrain(in vec2 p) {
    float rz = 0.0;
    float z = 1.0;
    vec2 d = vec2(0.0);
    float scl = 2.95;
    float zscl = -0.4;
    float zz = 5.0;
    for (int i = 0; i < 5; i++) {
        vec3 n = noised(p);
        d += pow(abs(n.yz), vec2(zz));
        d -= smoothstep(-0.5, 1.5, n.yz);
        zz -= 1.0;
        rz += z * n.x / (dot(d, d) + 0.85);
        z *= zscl;
        zscl *= 0.8;
        p = m2 * p * scl;
    }

    rz /= 1.0 - smoothstep(-0.5, 1.5, rz) + 0.75;
    return rz;
}

float map(vec3 p) {
    return p.y - terrain(p.zx * 0.07) * 2.7 - 1.0;
}

float march(in vec3 ro, in vec3 rd) {
    float t = 0.0;
    float d = map(rd * t + ro);
    float hitPrecision = 0.0001;
    for (int i = 0; i <= ITR; i++) {
        if (abs(d) < hitPrecision || t > FAR) break;
        hitPrecision = t * 0.0001;
        float rayLength = max(t * 0.02, 1.0);
        t += d * rayLength;
        d = map(rd * t + ro) * 0.7;
    }
    return t;
}

vec3 normal(in vec3 p, in float distanceToSurface) {
    float epsilon = max(0.0005 * distanceToSurface, 0.0001);
    vec2 e = vec2(-1.0, 1.0) * epsilon;
    return normalize(
            e.yxx * map(p + e.yxx)
            + e.xxy * map(p + e.xxy)
            + e.xyx * map(p + e.xyx)
            + e.yyy * map(p + e.yyy)
    );
}

float valueNoise(in vec2 x) {
    vec2 p = floor(x);
    vec2 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(p);
    float b = hash21(p + vec2(1.0, 0.0));
    float c = hash21(p + vec2(0.0, 1.0));
    float d = hash21(p + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float noise(in vec2 x) {
    return valueNoise(x * 2.56);
}

float fbm(in vec2 p) {
    float z = 0.5;
    float rz = 0.0;
    for (int i = 0; i < 3; i++) {
        rz += (sin(noise(p) * 5.0) * 0.5 + 0.5) * z;
        z *= 0.5;
        p *= 2.0;
    }
    return rz;
}

float bnoise(in vec2 p) {
    return fbm(p * 3.0);
}

vec3 bump(in vec3 p, in vec3 n, in float distanceToSurface) {
    float epsilon = max(0.005 * distanceToSurface, 0.0001);
    vec2 e = vec2(epsilon, 0.0);
    float n0 = bnoise(p.zx);
    vec3 d = vec3(bnoise(p.zx + e.xy) - n0, 1.0, bnoise(p.zx + e.yx) - n0) / e.x * 0.025;
    d -= n * dot(n, d);
    return normalize(n - d);
}

float curvature(in vec3 p, in float width) {
    vec2 e = vec2(-1.0, 1.0) * width;
    float t1 = map(p + e.yxx);
    float t2 = map(p + e.xxy);
    float t3 = map(p + e.xyx);
    float t4 = map(p + e.yyy);
    return 0.15 / e.y * (t1 + t2 + t3 + t4 - 4.0 * map(p));
}

vec3 fog(vec3 ro, vec3 rd, vec3 col, float distanceToSurface) {
    vec3 pos = ro + rd * distanceToSurface;
    float mx = (fbm(pos.zx * 0.1 - time * 0.05) - 0.5) * 0.2;
    float rayY = abs(rd.y) < 0.0001 ? (rd.y < 0.0 ? -0.0001 : 0.0001) : rd.y;

    const float densityFalloff = 1.0;
    float density = 0.3 * exp(-ro.y * densityFalloff)
            * (1.0 - exp(-distanceToSurface * rayY * densityFalloff)) / rayY;
    float sunAmount = max(dot(rd, lgt), 0.0);
    vec3 fogColor = mix(vec3(0.5, 0.2, 0.15) * 1.2,
            vec3(1.1, 0.6, 0.45) * 1.3, pow(sunAmount, 2.0) + mx * 0.5);
    return mix(col, fogColor, clamp(density + mx, 0.0, 1.0));
}

float linearStep(in float minimum, in float maximum, in float value) {
    return clamp((value - minimum) / (maximum - minimum), 0.0, 1.0);
}

vec3 scatter(vec3 ro, vec3 rd) {
    float sunAmount = max(dot(lgt, rd) * 0.5 + 0.5, 0.0);
    float horizonDistance = 13.0 - (ro + rd * FAR).y * 3.5;
    float horizon = linearStep(-1500.0, 0.0, horizonDistance)
            - linearStep(11.0, 500.0, horizonDistance);
    horizon *= pow(sunAmount, 0.04);

    vec3 col = vec3(0.0);
    col += pow(horizon, 200.0) * vec3(1.0, 0.7, 0.5) * 3.0;
    col += pow(horizon, 25.0) * vec3(1.0, 0.5, 0.25) * 0.3;
    col += pow(horizon, 7.0) * vec3(1.0, 0.4, 0.25) * 0.8;
    return col;
}

vec3 nmzHash33(vec3 q) {
    uvec3 p = uvec3(ivec3(q));
    p = p * uvec3(374761393U, 1103515245U, 668265263U) + p.zxy + p.yzx;
    p = p.yzx * (p.zxy ^ (p >> 3U));
    return vec3(p ^ (p >> 16U)) * (1.0 / vec3(0xffffffffU));
}

vec3 stars(in vec3 p) {
    vec3 color = vec3(0.0);
    float resolution = iResolution.x * 0.8;
    for (int i = 0; i < 3; i++) {
        float layer = float(i);
        vec3 q = fract(p * (0.15 * resolution)) - 0.5;
        vec3 id = floor(p * (0.15 * resolution));
        vec2 random = nmzHash33(id).xy;
        float star = 1.0 - smoothstep(0.0, 0.6, length(q));
        star *= step(random.x, 0.0005 + layer * layer * 0.001);
        color += star * (mix(vec3(1.0, 0.49, 0.1), vec3(0.75, 0.9, 1.0), random.y) * 0.25 + 0.75);
        p *= 1.4;
    }
    return color * color * 0.7;
}

void mainImage(out vec4 color, in vec2 fragCoord) {
    vec2 q = fragCoord / iResolution;
    vec2 p = q - 0.5;
    p.x *= iResolution.x / iResolution.y;

    vec2 mouse = iMouse / iResolution - 0.5;
    if (all(equal(mouse, vec2(-0.5)))) mouse = vec2(-0.2, 0.3);
    mouse.x *= 1.2;
    mouse -= vec2(1.2, -0.1);
    mouse.x *= iResolution.x / iResolution.y;
    mouse.x += sin(time * 0.15) * 0.2;

    vec3 ro = vec3(650.0, sin(time * 0.2) * 0.25 + 10.0, -time);
    vec3 eye = normalize(vec3(cos(mouse.x), -0.5 + mouse.y, sin(mouse.x)));
    vec3 right = normalize(vec3(cos(mouse.x + 1.5708), 0.0, sin(mouse.x + 1.5708)));
    vec3 up = normalize(cross(right, eye));
    vec3 rd = normalize((p.x * right + p.y * up) * 1.05 + eye);
    rd.y += abs(p.x * p.x * 0.015);
    rd = normalize(rd);

    float distanceToSurface = march(ro, rd);
    vec3 scattering = scatter(ro, rd);
    vec3 background = stars(rd) * (1.0 - clamp(dot(scattering, vec3(1.3)), 0.0, 1.0));
    vec3 col = background;

    if (distanceToSurface < FAR) {
        vec3 pos = ro + distanceToSurface * rd;
        vec3 nor = bump(pos, normal(pos, distanceToSurface), distanceToSurface);
        float ambient = clamp(0.5 + 0.5 * nor.y, 0.0, 1.0);
        float diffuse = clamp(dot(nor, lgt), 0.0, 1.0);
        float backLight = clamp(dot(nor, normalize(vec3(-lgt.x, 0.0, -lgt.z))), 0.0, 1.0);
        float specular = pow(clamp(dot(reflect(rd, nor), lgt), 0.0, 1.0), 500.0);
        float fresnel = pow(clamp(1.0 + dot(nor, rd), 0.0, 1.0), 2.0);
        vec3 brdf = ambient * vec3(0.10, 0.11, 0.12);
        brdf += backLight * vec3(0.15, 0.05, 0.04);
        brdf += 2.3 * diffuse * vec3(0.9, 0.4, 0.25);
        col = vec3(0.25, 0.25, 0.3);
        float broadCurvature = curvature(pos, 2.0);
        float fineCurvature = curvature(pos, 0.4) * 2.5;

        col += clamp(broadCurvature * 0.9, -1.0, 1.0) * vec3(0.25, 0.6, 0.5);
        col = col * brdf + col * specular * 0.1 + 0.1 * fresnel * col;
        col *= broadCurvature + 1.0;
        col *= fineCurvature + 1.0;
    }

    col = fog(ro, rd, col, distanceToSurface);
    col = mix(col, background, smoothstep(FAR - 150.0, FAR, distanceToSurface));
    col += scattering;

    col = pow(max(col, vec3(0.0)), vec3(0.93, 1.0, 1.0));
    col = mix(col, smoothstep(0.0, 1.0, col), 0.2);
    col *= pow(16.0 * q.x * q.y * (1.0 - q.x) * (1.0 - q.y), 0.1) * 0.9 + 0.1;
    color = vec4(col, 1.0);
}

void main() {
    mainImage(fragColor, gl_FragCoord.xy);
}
