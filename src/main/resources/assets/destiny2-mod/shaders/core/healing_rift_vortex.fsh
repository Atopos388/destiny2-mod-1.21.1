#version 150

uniform float Time;
uniform float Intensity;

in vec2 vortexUv;
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
    float height = 1.0 - vortexUv.y;
    float horizontal = vortexUv.x * 2.0 - 1.0;
    float twist =
        sin(height * 9.0 - Time * 1.65) * (0.08 + height * 0.16) +
        sin(height * 4.2 + Time * 0.73) * 0.055;
    float width = mix(0.30, 0.92, height);
    float softBody = 1.0 - smoothstep(width * 0.32, width, abs(horizontal - twist));

    float verticalFade =
        smoothstep(0.0, 0.10, height) *
        (1.0 - smoothstep(0.72, 1.0, height));
    float mistNoise = noise(vec2(
        horizontal * 1.35 + Time * 0.08,
        height * 4.8 - Time * 0.24
    ));
    float softBands = 0.5 + 0.5 * sin(
        height * 13.0 -
        Time * 1.18 +
        mistNoise * 2.4
    );
    softBands = smoothstep(0.12, 0.92, softBands);

    float alpha =
        softBody *
        verticalFade *
        (0.026 + mistNoise * 0.040 + softBands * 0.032) *
        Intensity *
        vertexColor.a;

    if (alpha < 0.002) discard;
    vec3 color = mix(
        vec3(0.69, 0.86, 1.0),
        vec3(0.96, 0.99, 1.0),
        clamp(softBands * 0.55 + mistNoise * 0.34, 0.0, 1.0)
    );
    fragColor = vec4(color * vertexColor.rgb, clamp(alpha, 0.0, 0.105));
}
