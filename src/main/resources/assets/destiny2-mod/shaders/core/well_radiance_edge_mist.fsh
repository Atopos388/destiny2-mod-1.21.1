#version 150

uniform float Time;
uniform float Intensity;

in vec2 mistUv;
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

void main() {
    float height = 1.0 - mistUv.y;
    float angle = mistUv.x * 6.2831853;
    float verticalFade =
        smoothstep(0.0, 0.075, height) *
        (1.0 - smoothstep(0.62, 1.0, height));

    float risingNoise = noise(vec2(
        cos(angle) * 2.25 + Time * 0.11,
        sin(angle) * 2.25 + height * 4.8 - Time * 0.64
    ));
    float risingBands = 0.5 + 0.5 * sin(
        height * 12.5 +
        sin(angle * 5.0 - Time * 0.37) * 1.45 -
        Time * 2.15 +
        risingNoise * 2.8
    );
    risingBands = smoothstep(0.16, 0.90, risingBands);
    float connectedBase = 1.0 - smoothstep(0.12, 0.46, height);

    float alpha =
        verticalFade *
        (
            connectedBase * 0.052 +
            risingNoise * 0.056 +
            risingBands * 0.050
        ) *
        Intensity *
        vertexColor.a;
    if (alpha < 0.002) discard;

    vec3 warmGold = vec3(1.0, 0.74, 0.34);
    vec3 whiteGold = vec3(1.0, 0.985, 0.86);
    vec3 color = mix(
        warmGold,
        whiteGold,
        clamp(risingBands * 0.62 + risingNoise * 0.34, 0.0, 1.0)
    );
    fragColor = vec4(color * vertexColor.rgb, clamp(alpha, 0.0, 0.16));
}
