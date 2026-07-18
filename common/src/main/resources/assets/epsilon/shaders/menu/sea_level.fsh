#version 410 core

layout(std140) uniform GlslSandboxInfo {
    vec4 SandboxResolutionTime;
    vec4 SandboxMouse;
};

layout(location = 0) out vec4 fragColor;

#define resolution SandboxResolutionTime.xy
#define time SandboxResolutionTime.z

void mainImage(out vec4 o, vec2 u) {
    float s = 0.3, i = 0.0, n;
    vec3 r = vec3(resolution, 1.0), p = vec3(0.0);

    float bob = sin(time * 0.75) * 0.045 + sin(time * 1.37 + 1.4) * 0.018;
    float sway = sin(time * 0.42 + 0.8) * 0.035 + sin(time * 0.91) * 0.012;
    float roll = sin(time * 0.55) * 0.025 + sin(time * 1.13 + 0.7) * 0.008;
    float zoom = 1.0 + sin(time * 0.31 + 0.5) * 0.018;
    vec2 camera = u - resolution * 0.5;
    float c = cos(roll), sn = sin(roll);
    u = mat2(c, -sn, sn, c) * camera / (resolution.y * zoom) + vec2(sway, bob);

    for (u = u - s; i++ < 32.0 && ++s > 0.001;) {
        for (p += vec3(u * s, s), s = p.y, n = 0.01; n < 1.0; n += n) {
            s += abs(dot(sin(p.z + time + p / n), r / r)) * n * 0.1;
        }
    }
    o = tanh(i * vec4(5.0, 2.0, 1.0, 0.0) / length(u - 0.1) / 5e2);
    o.a = 1.0;
}

void main() {
    mainImage(fragColor, gl_FragCoord.xy);
}
