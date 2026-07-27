#version 150

uniform vec3 CameraPos;
uniform float Time;
uniform float Pulse;
in vec4 vertexColor;
in vec3 spherePos;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x),
            mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
        mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
            mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y),
        f.z
    );
}

float fbm(vec3 p) {
    float value = 0.0;
    float amplitude = 0.55;
    for (int i = 0; i < 4; ++i) {
        value += noise(p) * amplitude;
        p = p * 2.03 + vec3(7.1, 11.7, 5.3);
        amplitude *= 0.48;
    }
    return value;
}

bool intersectSphere(vec3 ro, vec3 rd, out float nearT, out float farT) {
    float b = dot(ro, rd);
    float c = dot(ro, ro) - 1.0;
    float h = b * b - c;
    if (h < 0.0) return false;
    h = sqrt(h);
    nearT = -b - h;
    farT = -b + h;
    return true;
}

void main() {
    vec3 surface = normalize(spherePos);
    vec3 rayOrigin = CameraPos;
    vec3 rayDirection = normalize(surface - rayOrigin);
    float nearT;
    float farT;
    if (!intersectSphere(rayOrigin, rayDirection, nearT, farT)) discard;

    nearT = max(nearT, 0.0);
    float thickness = max(farT - nearT, 0.0);
    float impact = length(cross(rayOrigin, rayDirection));
    float edge = smoothstep(1.0, 0.50, impact);
    float rim = pow(1.0 - edge, 2.4);

    vec3 flowPosition = surface * 3.2;
    flowPosition.y += Time * 0.72;
    flowPosition.xz += vec2(Time * 0.18, -Time * 0.13);
    float flowingNoise = fbm(flowPosition);
    float filaments = smoothstep(0.54, 0.86, flowingNoise + sin(surface.y * 14.0 - Time * 2.2) * 0.10);
    float innerGlow = smoothstep(0.96, 0.08, impact) * (0.66 + flowingNoise * 0.42);
    float breath = Pulse * (0.92 + 0.08 * sin(Time * 3.1));

    vec3 warmWhite = vec3(1.0, 0.93, 0.76);
    vec3 clearWhite = vec3(1.0, 1.0, 0.98);
    vec3 color = mix(warmWhite, clearWhite, clamp(innerGlow + filaments * 0.55, 0.0, 1.0));
    color *= (0.72 + innerGlow * 0.72 + rim * 0.55 + filaments * 0.36) * breath;

    float alpha = (0.10 + thickness * 0.10 + innerGlow * 0.12 + rim * 0.24 + filaments * 0.08);
    alpha *= smoothstep(1.03, 0.88, impact);
    fragColor = vec4(color * vertexColor.rgb, clamp(alpha * vertexColor.a, 0.0, 0.58));
}
