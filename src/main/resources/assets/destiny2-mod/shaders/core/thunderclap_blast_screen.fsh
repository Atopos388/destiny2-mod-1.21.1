#version 150

uniform float Strength;
uniform float FlashStrength;
uniform float Time;

in vec2 screenUv;
out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    vec2 centered = screenUv * 2.0 - 1.0;
    float radius = length(centered * vec2(0.88, 1.0));
    float centerWash = 1.0 - smoothstep(0.06, 1.05, radius);
    float softHalo = 1.0 - smoothstep(0.30, 1.30, radius);
    float impactCore = 1.0 - smoothstep(0.0, 0.66, radius);
    float impactWash = 1.0 - smoothstep(0.16, 1.18, radius);
    float grain = hash21(floor(screenUv * vec2(320.0, 180.0)) + floor(Time * 18.0));
    float ambientAlpha = Strength * (centerWash * 0.24 + softHalo * 0.045);
    float impactAlpha = FlashStrength * (impactCore * 0.46 + impactWash * 0.20 + softHalo * 0.055);
    float alpha = clamp(ambientAlpha + impactAlpha, 0.0, 0.74);
    vec3 arcBlue = mix(vec3(0.20, 0.55, 1.0), vec3(0.88, 0.97, 1.0), centerWash);
    vec3 color = mix(arcBlue, vec3(0.96, 0.99, 1.0), FlashStrength * (0.58 + impactCore * 0.42));
    color *= 0.96 + grain * 0.08;
    fragColor = vec4(color, alpha);
}
