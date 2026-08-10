#version 150

uniform float Time;
uniform float Phase;
uniform float Opacity;

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 31.7);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x),
               mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}

void main() {
    vec2 centered = texCoord * 2.0 - 1.0;
    float radius = length(centered);
    float angle = atan(centered.y, centered.x) / 6.2831853 + 0.5;
    vec2 p = vec2(radius, angle);
    float flowA = noise(p * vec2(5.1, 7.2) + vec2(Time * 0.64, -Time * 0.37));
    float flowB = noise(p * vec2(10.7, 13.7) + vec2(-Time * 0.51, Time * 0.72));
    float broken = smoothstep(0.30, 0.72, flowA * 0.64 + flowB * 0.45);
    float disc = (1.0 - smoothstep(0.48 + flowA * 0.10, 1.0, radius)) * (0.14 + broken * 0.86);
    float center = 1.0 - smoothstep(0.0, 0.34, radius);
    float ringPosition = mix(0.18, 0.92, Phase);
    float ring = 1.0 - smoothstep(0.025, 0.105, abs(radius - ringPosition));
    ring *= (0.22 + broken * 0.78) * (1.0 - Phase * 0.72);
    float alpha = Opacity * (disc * 0.32 + center * 0.48 + ring * 0.46) * vertexColor.a;
    if (alpha < 0.003) discard;
    vec3 deepBlue = vec3(0.055, 0.20, 0.68);
    vec3 iceBlue = vec3(0.38, 0.82, 1.30);
    vec3 whiteBlue = vec3(0.86, 0.98, 1.34);
    vec3 color = mix(deepBlue, iceBlue, broken);
    color = mix(color, whiteBlue, center * 0.86 + ring * 0.40);
    fragColor = vec4(color * vertexColor.rgb, clamp(alpha, 0.0, 0.52));
}
