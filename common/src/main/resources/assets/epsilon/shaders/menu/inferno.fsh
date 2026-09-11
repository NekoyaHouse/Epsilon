#version 410 core

layout(std140) uniform GlslSandboxInfo {
    vec4 SandboxResolutionTime;
    vec4 SandboxMouse;
};

layout(location = 0) out vec4 fragColor;

#define iResolution SandboxResolutionTime.xy
#define iTime SandboxResolutionTime.z

float hash31(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float valueNoise2(vec2 p) {
    vec2 cell = floor(p);
    vec2 local = fract(p);
    local = local * local * (3.0 - 2.0 * local);

    float a = hash21(cell);
    float b = hash21(cell + vec2(1.0, 0.0));
    float c = hash21(cell + vec2(0.0, 1.0));
    float d = hash21(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, local.x), mix(c, d, local.x), local.y);
}

vec3 sampleNoise3(vec2 uv) {
    vec2 p = uv * 256.0;
    return vec3(
            valueNoise2(p + vec2(17.17, 31.73)),
            valueNoise2(p + vec2(47.11, 13.97)),
            valueNoise2(p + vec2(91.07, 63.61))
    );
}

float noise(in vec3 x) {
    vec3 cell = floor(x);
    vec3 local = fract(x);
    local = local * local * (3.0 - 2.0 * local);

    float n000 = hash31(cell + vec3(0.0, 0.0, 0.0));
    float n100 = hash31(cell + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(cell + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(cell + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(cell + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(cell + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(cell + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(cell + vec3(1.0, 1.0, 1.0));

    float z0 = mix(mix(n000, n100, local.x), mix(n010, n110, local.x), local.y);
    float z1 = mix(mix(n001, n101, local.x), mix(n011, n111, local.x), local.y);
    return mix(z0, z1, local.z);
}

vec4 map(vec3 p) {
    float density = 0.2 - p.y;

    p = -7.0 * p / max(dot(p, p), 0.001);

    float cosine = cos(density - 0.25 * iTime);
    float sine = sin(density - 0.25 * iTime);
    p.xz = mat2(cosine, -sine, sine, cosine) * p.xz;

    vec3 verticalMotion = vec3(0.0, 1.0, 0.0) * iTime;
    vec3 q = p - verticalMotion;
    float smoke = 0.50000 * noise(q);
    q = q * 2.02 - verticalMotion;
    smoke += 0.25000 * noise(q);
    q = q * 2.03 - verticalMotion;
    smoke += 0.12500 * noise(q);
    q = q * 2.01 - verticalMotion;
    smoke += 0.06250 * noise(q);
    q = q * 2.02 - verticalMotion;
    smoke += 0.03125 * noise(q);

    density += 4.0 * smoke;
    vec3 color = mix(vec3(1.0, 0.9, 0.8), vec3(0.4, 0.15, 0.1), density)
            + 0.05 * sin(p);
    return vec4(color, density);
}

vec3 raymarch(in vec3 ro, in vec3 rd, in vec2 pixel) {
    vec4 sum = vec4(0.0);
    float distanceAlongRay = 0.05
            * fract(10.5421 * dot(vec2(0.0149451, 0.038921), pixel));

    for (int i = 0; i < 150; i++) {
        vec3 pos = ro + distanceAlongRay * rd;
        vec4 color = map(pos);
        if (color.a > 0.0) {
            color.a = min(color.a, 1.0);
            color.rgb *= mix(
                    3.1 * vec3(1.0, 0.5, 0.05),
                    vec3(0.48, 0.53, 0.5),
                    clamp((pos.y - 0.2) / 1.9, 0.0, 1.0)
            );
            color.a *= 0.6;
            color.rgb *= color.a;
            sum += color * (1.0 - sum.a);
            if (sum.a > 0.99) break;
        }
        distanceAlongRay += 0.05;
    }

    return clamp(sum.rgb, 0.0, 1.0);
}

void mainImage(out vec4 color, in vec2 fragCoord) {
    vec2 p = (2.0 * fragCoord - iResolution) / iResolution.y;

    vec3 ro = 4.0 * normalize(vec3(1.0, 1.5, 0.0));
    vec3 target = vec3(0.0, 1.0, 0.0)
            + 0.05 * (-1.0 + 2.0 * sampleNoise3(iTime * vec2(0.013, 0.008)));
    float cameraRoll = 0.5 * cos(0.7 * iTime);

    vec3 forward = normalize(target - ro);
    vec3 right = normalize(cross(vec3(sin(cameraRoll), cos(cameraRoll), 0.0), forward));
    vec3 up = normalize(cross(forward, right));
    vec3 rd = normalize(p.x * right + p.y * up + 2.0 * forward);

    vec3 col = raymarch(ro, rd, fragCoord);
    col = col * 0.5 + 0.5 * col * col * (3.0 - 2.0 * col);

    vec2 q = fragCoord / iResolution;
    col *= 0.2 + 0.8 * pow(16.0 * q.x * q.y * (1.0 - q.x) * (1.0 - q.y), 0.1);
    color = vec4(col, 1.0);
}

void main() {
    mainImage(fragColor, gl_FragCoord.xy);
}
