#version 410 core

layout(std140) uniform GlslSandboxInfo {
    vec4 SandboxResolutionTime;
    vec4 SandboxMouse;
};

layout(location = 0) out vec4 fragColor;

#define iResolution SandboxResolutionTime.xy
#define iTime SandboxResolutionTime.z
#define iMouse SandboxMouse.zw
#define time iTime

mat2 mm2(in float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, s, -s, c);
}

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash31(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(float t) {
    float i = floor(t);
    float f = fract(t);
    f = f * f * (3.0 - 2.0 * f);
    return mix(hash11(i), hash11(i + 1.0), f);
}

float moy = 0.0;

float noise(in vec3 p) {
    vec3 ip = floor(p);
    vec3 fp = fract(p);
    fp = fp * fp * (3.0 - 2.0 * fp);

    float n000 = hash31(ip + vec3(0.0, 0.0, 0.0));
    float n100 = hash31(ip + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(ip + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(ip + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(ip + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(ip + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(ip + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(ip + vec3(1.0, 1.0, 1.0));

    float z0 = mix(mix(n000, n100, fp.x), mix(n010, n110, fp.x), fp.y);
    float z1 = mix(mix(n001, n101, fp.x), mix(n011, n111, fp.x), fp.y);
    return mix(z0, z1, fp.z);
}

float fbm(in vec3 x) {
    float rz = 0.0;
    float a = 0.35;
    for (int i = 0; i < 2; i++) {
        rz += noise(x) * a;
        a *= 0.35;
        x *= 4.0;
    }
    return rz;
}

float path(in float x) {
    return sin(x * 0.01 - 3.1415) * 28.0 + 6.5;
}

float map(vec3 p) {
    return p.y * 0.07 + (fbm(p * 0.3) - 0.1)
            + sin(p.x * 0.24 + sin(p.z * 0.01) * 7.0) * 0.22 + 0.15
            + sin(p.z * 0.08) * 0.05;
}

float march(in vec3 ro, in vec3 rd) {
    float precis = 0.3;
    float h = 1.0;
    float d = 0.0;
    for (int i = 0; i < 17; i++) {
        if (abs(h) < precis || d > 70.0) break;
        d += h;
        vec3 pos = ro + rd * d;
        pos.y += 0.5;
        h = map(pos) * 7.0;
    }
    return d;
}

vec3 lgt = vec3(0.0);

float mapV(vec3 p) {
    return clamp(-map(p), 0.0, 1.0);
}

vec4 marchV(in vec3 ro, in vec3 rd, in float t, in vec3 bgc) {
    vec4 rz = vec4(0.0);

    for (int i = 0; i < 150; i++) {
        if (rz.a > 0.99 || t > 200.0) break;

        vec3 pos = ro + t * rd;
        float den = mapV(pos);

        vec4 col = vec4(mix(vec3(0.8, 0.75, 0.85), vec3(0.0), den), den);
        col.xyz *= mix(bgc * bgc * 2.5,
                mix(vec3(0.1, 0.2, 0.55), vec3(0.8, 0.85, 0.9), moy * 0.4),
                clamp(-(den * 40.0) * pos.y * 0.03 - moy * 0.5, 0.0, 1.0));
        col.rgb += clamp((1.0 - den * 6.0) + pos.y * 0.13 + 0.55, 0.0, 1.0)
                * 0.35 * mix(bgc, vec3(1.0), 0.7);
        col += clamp(den * pos.y * 0.15, -0.02, 0.0);
        float shadow = 1.0 - smoothstep(0.0, 0.2 + moy * 0.05, mapV(pos + lgt));
        col *= shadow * 0.85 + 0.15;

        col.a *= 0.95;
        col.rgb *= col.a;
        rz += col * (1.0 - rz.a);

        t += max(0.3, (2.0 - den * 30.0) * t * 0.011);
    }

    return clamp(rz, 0.0, 1.0);
}

float pent(in vec2 p) {
    vec2 q = abs(p);
    return max(max(q.x * 1.176 - p.y * 0.385, q.x * 0.727 + p.y), -p.y * 1.237);
}

float pentPow(in vec2 p, float exponent) {
    return pow(max(pent(p), 0.0), exponent);
}

vec3 lensFlare(vec2 p, vec2 pos) {
    vec2 q = p - pos;
    vec2 dist = p * length(p) * 0.75;
    float ang = atan(q.x, q.y);
    vec2 pp = mix(p, dist, 0.5);
    float sz = 0.01;
    float rz = pow(abs(fract(ang * 0.8 + 0.12) - 0.5), 3.0) * noise(ang * 15.0) * 0.5;
    rz *= 1.0 - smoothstep(0.0, 1.0, dot(q, q));
    rz *= smoothstep(0.0, 0.01, dot(q, q));
    rz += 1.0 / (1.0 + 30.0 * max(pent(dist + 0.8 * pos), 0.0)) * 0.17;
    rz += clamp(sz - pentPow(pp + 0.15 * pos, 1.55), 0.0, 1.0) * 5.0;
    rz += clamp(sz - pentPow(pp + 0.1 * pos, 2.4), 0.0, 1.0) * 4.0;
    rz += clamp(sz - pentPow(pp - 0.05 * pos, 1.2), 0.0, 1.0) * 4.0;
    rz += clamp(sz - pentPow(pp + 0.5 * pos, 1.7), 0.0, 1.0) * 4.0;
    rz += clamp(sz - pentPow(pp + 0.3 * pos, 1.9), 0.0, 1.0) * 3.0;
    rz += clamp(sz - pentPow(pp - 0.2 * pos, 1.3), 0.0, 1.0) * 4.0;
    return vec3(clamp(rz, 0.0, 1.0));
}

mat3 rotX(float a) {
    float sa = sin(a), ca = cos(a);
    return mat3(1.0, 0.0, 0.0, 0.0, ca, sa, 0.0, -sa, ca);
}

mat3 rotY(float a) {
    float sa = sin(a), ca = cos(a);
    return mat3(ca, 0.0, sa, 0.0, 1.0, 0.0, -sa, 0.0, ca);
}

mat3 rotZ(float a) {
    float sa = sin(a), ca = cos(a);
    return mat3(ca, sa, 0.0, -sa, ca, 0.0, 0.0, 0.0, 1.0);
}

void mainImage(out vec4 color, in vec2 fragCoord) {
    vec2 q = fragCoord / iResolution;
    vec2 p = q - 0.5;
    p.x *= iResolution.x / iResolution.y;

    vec2 mo = iMouse / iResolution;
    moy = mo.y;
    float st = sin(time * 0.3 - 1.3) * 0.2;
    vec3 ro = vec3(0.0, -2.0 + sin(time * 0.3 - 1.0) * 2.0, time * 30.0);
    ro.x = path(ro.z);

    vec3 ta = ro + vec3(0.0, 0.0, 1.0);
    vec3 fw = normalize(ta - ro);
    vec3 uu = normalize(cross(vec3(0.0, 1.0, 0.0), fw));
    vec3 vv = normalize(cross(fw, uu));
    const float zoom = 1.0;
    vec3 rd = normalize(p.x * uu + p.y * vv - zoom * fw);

    float rox = sin(time * 0.2) * 0.6 + 2.9;
    rox += smoothstep(0.6, 1.2, sin(time * 0.25)) * 3.5;
    float roy = sin(time * 0.5) * 0.2;
    mat3 rotation = rotX(-roy) * rotY(-rox + st * 1.5) * rotZ(st);
    mat3 invRotation = rotZ(-st) * rotY(rox - st * 1.5) * rotX(roy);
    rd = rd * rotation;
    rd.y -= dot(p, p) * 0.06;
    rd = normalize(rd);

    vec3 col = vec3(0.0);
    lgt = normalize(vec3(-0.3, mo.y + 0.1, 1.0));
    float rdl = clamp(dot(rd, lgt), 0.0, 1.0);

    vec3 hor = mix(vec3(0.9, 0.6, 0.7) * 0.35, vec3(0.5, 0.05, 0.05), rdl);
    hor = mix(hor, vec3(0.5, 0.8, 1.0), mo.y);
    col += mix(vec3(0.2, 0.2, 0.6), hor,
            exp2(-(1.0 + 3.0 * (1.0 - rdl)) * max(abs(rd.y), 0.0))) * 0.6;
    col += 0.8 * vec3(1.0, 0.9, 0.9) * exp2(rdl * 650.0 - 650.0);
    col += 0.3 * vec3(1.0, 1.0, 0.1) * exp2(rdl * 100.0 - 100.0);
    col += 0.5 * vec3(1.0, 0.7, 0.0) * exp2(rdl * 50.0 - 50.0);
    col += 0.4 * vec3(1.0, 0.0, 0.05) * exp2(rdl * 10.0 - 10.0);
    vec3 bgc = col;

    float rz = march(ro, rd);
    if (rz < 70.0) {
        vec4 res = marchV(ro, rd, rz - 5.0, bgc);
        col = col * (1.0 - res.w) + res.xyz;
    }

    vec3 proj = -lgt * invRotation;
    col += 1.4 * vec3(0.7, 0.7, 0.4)
            * clamp(lensFlare(p, -proj.xy / proj.z * zoom) * proj.z, 0.0, 1.0);

    float g = smoothstep(0.03, 0.97, mo.x);
    col = mix(mix(col, col.brg * vec3(1.0, 0.75, 1.0), clamp(g * 2.0, 0.0, 1.0)),
            col.bgr, clamp((g - 0.5) * 2.0, 0.0, 1.0));

    col = clamp(col, 0.0, 1.0);
    col = col * 0.5 + 0.5 * col * col * (3.0 - 2.0 * col);
    col = pow(col, vec3(0.416667)) * 1.055 - 0.055;
    col *= pow(16.0 * q.x * q.y * (1.0 - q.x) * (1.0 - q.y), 0.12);

    color = vec4(col, 1.0);
}

void main() {
    mainImage(fragColor, gl_FragCoord.xy);
}
