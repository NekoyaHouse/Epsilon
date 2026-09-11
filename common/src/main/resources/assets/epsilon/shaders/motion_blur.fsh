#version 410 core

uniform sampler2D InputSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform MotionBlurData {
    mat4 ViewInverse;
    mat4 ProjectionInverse;
    mat4 PreviousView;
    mat4 PreviousProjection;
    vec4 CameraDelta;
    vec4 ScreenData;
    ivec4 BlurSettings;
};

in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int MAX_SAMPLES = 256;

vec2 reproject(vec3 screenPosition) {
    float ndcDepth = ScreenData.w > 0.5 ? screenPosition.z : screenPosition.z * 2.0 - 1.0;
    vec3 ndc = vec3(screenPosition.xy * 2.0 - 1.0, ndcDepth);
    vec4 viewPosition = ProjectionInverse * vec4(ndc, 1.0);
    viewPosition /= viewPosition.w;

    vec3 previousRelativePosition = (ViewInverse * vec4(viewPosition.xyz, 1.0)).xyz + CameraDelta.xyz;
    vec4 previousClip = PreviousProjection * PreviousView * vec4(previousRelativePosition, 1.0);
    if (abs(previousClip.w) < 1.0e-6) return screenPosition.xy;
    return previousClip.xy / previousClip.w * 0.5 + 0.5;
}

vec2 clampLength(vec2 velocity) {
    float lengthSquared = dot(velocity, velocity);
    return lengthSquared > 0.16 ? velocity * (0.4 * inversesqrt(lengthSquared)) : velocity;
}

float noise(vec2 position) {
    return fract(52.9829189 * fract(0.06711056 * position.x + 0.00583715 * position.y));
}

void main() {
    vec4 source = texture(InputSampler, texCoord);
    ivec2 textureSizePixels = textureSize(DepthSampler, 0);
    ivec2 texel = clamp(ivec2(gl_FragCoord.xy), ivec2(0), textureSizePixels - 1);
    float depth = texelFetch(DepthSampler, texel, 0).x;

    if (BlurSettings.z == 1) {
        for (int x = -1; x <= 1; ++x) {
            for (int y = -1; y <= 1; ++y) {
                ivec2 sampleTexel = clamp(texel + ivec2(x, y), ivec2(0), textureSizePixels - 1);
                depth = max(depth, texelFetch(DepthSampler, sampleTexel, 0).x);
            }
        }
    } else {
        depth = 0.0;
    }

    vec2 velocity = clampLength(texCoord - reproject(vec3(texCoord, depth)));
    float speed = length(velocity);
    if (speed < 1.0e-6) {
        fragColor = source;
        return;
    }

    int sampleLimit = clamp(BlurSettings.x, 4, MAX_SAMPLES);
    int dynamicSamples = clamp(int(ceil(speed * float(sampleLimit))), 4, sampleLimit);
    vec2 sampleStep = ScreenData.z * velocity / float(dynamicSamples);
    float centerOffset = BlurSettings.y == 0 ? 0.0 : -float(dynamicSamples) * 0.5;
    vec2 seed = texCoord * ScreenData.xy;
    vec3 colorSquares = vec3(0.0);

    for (int i = 0; i < MAX_SAMPLES; ++i) {
        if (i >= dynamicSamples) break;
        float sampleIndex = float(i);
        float jitter = noise(seed + vec2(sampleIndex, sampleIndex * 1.4));
        vec2 samplePosition = texCoord + (sampleIndex + centerOffset + jitter) * sampleStep;
        vec3 sampleColor = texture(InputSampler, samplePosition).rgb;
        colorSquares += sampleColor * sampleColor;
    }

    fragColor = vec4(sqrt(colorSquares / float(dynamicSamples)), source.a);
}
