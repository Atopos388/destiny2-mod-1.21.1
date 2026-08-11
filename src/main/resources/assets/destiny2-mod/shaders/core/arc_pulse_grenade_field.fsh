#version 150

uniform float Time;
uniform float Mode;
uniform float Progress;
uniform float Opacity;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i), hash(i + vec2(1, 0)), f.x),
        mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x),
        f.y
    );
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.58;
    for (int i = 0; i < 4; ++i) {
        value += noise(p) * amplitude;
        p = p * 2.07 + vec2(7.1, 11.9);
        amplitude *= 0.46;
    }
    return value;
}

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    float radius = length(p);
    if (radius > 1.0) discard;
    float angle = atan(p.y, p.x);
    float coarse = fbm(p * 3.6 + vec2(Time * 0.83, -Time * 0.57));
    float detail = fbm(p * 8.4 + vec2(-Time * 1.37, Time * 0.91));
    float angularBreak = sin(angle * 9.0 + coarse * 5.2 - Time * 6.1) * 0.5 + 0.5;

    vec3 paleCyan = vec3(0.56, 0.918, 1.0);
    vec3 arcBlue = vec3(0.227, 0.561, 1.0);
    vec3 deepBlue = vec3(0.09, 0.294, 0.72);
    vec3 color;
    float alpha;

    if (Mode < 0.5) {
        float envelope = 1.0 - smoothstep(0.10, 1.0, radius);
        float brokenMist = smoothstep(0.31, 0.79, coarse * 0.66 + detail * 0.35 + angularBreak * 0.18);
        float hotCenter = 1.0 - smoothstep(0.0, 0.24, radius);
        color = mix(deepBlue, arcBlue, brokenMist * 0.74 + hotCenter * 0.30);
        color = mix(color, paleCyan, hotCenter * 0.56);
        alpha = envelope * (0.025 + brokenMist * 0.17 + hotCenter * 0.18) * Opacity;
    } else {
        float frontRadius = mix(0.08, 0.92, smoothstep(0.0, 1.0, Progress));
        float width = mix(0.18, 0.095, Progress);
        float softFront = 1.0 - smoothstep(width * 0.35, width, abs(radius - frontRadius));
        float innerWash = (1.0 - smoothstep(0.0, frontRadius, radius)) * (1.0 - Progress) * 0.38;
        float breakup = smoothstep(0.23, 0.82, coarse * 0.57 + detail * 0.24 + angularBreak * 0.36);
        float edgeFade = 1.0 - smoothstep(0.91, 1.0, radius);
        color = mix(deepBlue, arcBlue, clamp(softFront * 0.68 + breakup * 0.46, 0.0, 1.0));
        color = mix(color, paleCyan, softFront * breakup * 0.44);
        alpha = (softFront * (0.20 + breakup * 0.42) + innerWash * breakup) * edgeFade * Opacity;
    }

    alpha *= vertexColor.a;
    if (alpha < 0.003) discard;
    fragColor = vec4(color * vertexColor.rgb, clamp(alpha, 0.0, 0.91));
}
