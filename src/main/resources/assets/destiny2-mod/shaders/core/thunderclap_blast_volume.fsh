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
    for (int octave = 0; octave < 4; ++octave) {
        value += noise(p) * amplitude;
        p = p * 2.03 + vec2(7.1, 11.7);
        amplitude *= 0.48;
    }
    return value;
}

void main() {
    float vertical = texCoord.x;
    float angular = texCoord.y;
    vec2 domain = vec2(vertical * 4.4 - Time * 1.65, angular * 8.0 + Time * 0.22);
    float broad = fbm(domain);
    float detail = fbm(domain * 2.15 + vec2(-Time * 0.72, Time * 0.43));
    float brokenFlow = smoothstep(0.46, 0.82, broad * 0.68 + detail * 0.44);
    float movingBands = 0.70 + sin(vertical * 26.0 - Time * 9.2 + broad * 5.0) * 0.30;

    float neckFade = smoothstep(0.0, 0.07, vertical);
    float openRimBreakup = 1.0 - smoothstep(0.80, 1.0, vertical) * (0.42 + detail * 0.44);
    float verticalBreakup = smoothstep(0.20, 0.58, fbm(vec2(vertical * 6.1 + Time, angular * 5.3)));
    float climbingEnergy = smoothstep(0.10, 0.58, vertical);
    float rimEnergy = smoothstep(0.72, 0.96, vertical);
    float density = neckFade * openRimBreakup * (0.018 + brokenFlow * 0.61) * movingBands;
    density *= 0.24 + verticalBreakup * 0.76;
    density *= 1.0 + climbingEnergy * 0.10 + rimEnergy * brokenFlow * 0.12;

    vec3 deepArc = vec3(0.035, 0.19, 0.72);
    vec3 brightArc = vec3(0.22, 0.72, 1.35);
    vec3 rimWhite = vec3(0.70, 0.91, 1.42);
    vec3 color = mix(deepArc, brightArc, broad * 0.72 + detail * 0.28);
    color = mix(color, rimWhite, rimEnergy * brokenFlow * 0.12);
    float alpha = vertexColor.a * density;
    if (alpha < 0.003) discard;
    fragColor = vec4(color * vertexColor.rgb, clamp(alpha, 0.0, 0.15));
}
