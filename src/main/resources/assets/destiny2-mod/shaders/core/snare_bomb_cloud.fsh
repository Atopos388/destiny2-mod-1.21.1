#version 150

uniform float Time;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
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

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.56;
    for (int octave = 0; octave < 3; ++octave) {
        value += noise(p) * amplitude;
        p = p * 2.07 + vec2(7.13, 11.71);
        amplitude *= 0.48;
    }
    return value;
}

void main() {
    vec2 centered = texCoord * 2.0 - 1.0;
    float radial = length(centered);

    vec2 slowDomain = centered * 1.65 + vec2(Time * 0.21, -Time * 0.16);
    float broad = fbm(slowDomain);
    vec2 warp = vec2(
        fbm(slowDomain * 1.31 + 5.7),
        fbm(slowDomain * 1.19 - 8.3)
    ) - 0.5;
    float detail = fbm(centered * 3.25 + warp * 0.82 + vec2(-Time * 0.34, Time * 0.27));

    float disturbedRadius = radial + (broad - 0.5) * 0.24;
    float feather = 1.0 - smoothstep(0.34, 1.12, disturbedRadius);
    feather *= feather;
    float lowerFeather = 1.0 - smoothstep(0.70, 1.0, texCoord.y);
    float cloud = smoothstep(0.22, 0.72, broad * 0.62 + detail * 0.46);
    float density = feather * lowerFeather * (0.42 + cloud * 0.58);

    vec3 deepViolet = vec3(0.10, 0.035, 0.27);
    vec3 blueViolet = vec3(0.29, 0.18, 0.65);
    vec3 magentaMist = vec3(0.48, 0.18, 0.62);
    vec3 color = mix(deepViolet, blueViolet, broad);
    color = mix(color, magentaMist, detail * 0.34);
    color *= vertexColor.rgb * 1.35;

    float alpha = vertexColor.a * density;
    if (alpha < 0.002) discard;
    fragColor = vec4(color, clamp(alpha, 0.0, 0.62));
}
