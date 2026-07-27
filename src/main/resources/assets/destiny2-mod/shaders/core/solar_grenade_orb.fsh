#version 150

uniform vec3 CameraPos;
uniform float Time;
uniform float Heat;
uniform float Opacity;

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
    float amplitude = 0.56;
    for (int i = 0; i < 4; ++i) {
        value += noise(p) * amplitude;
        p = p * 2.04 + vec3(6.7, 9.3, 12.1);
        amplitude *= 0.47;
    }
    return value;
}

bool intersectSphere(vec3 rayOrigin, vec3 rayDirection, out float nearT, out float farT) {
    float b = dot(rayOrigin, rayDirection);
    float c = dot(rayOrigin, rayOrigin) - 1.0;
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
    float center = smoothstep(1.02, 0.03, impact);
    float rim = pow(1.0 - smoothstep(0.34, 0.98, impact), 0.62);

    vec3 flow = surface * (3.0 + Heat * 0.28);
    flow.y -= Time * (1.15 + Heat * 0.24);
    flow.xz += vec2(Time * 0.31, -Time * 0.23);
    float coarse = fbm(flow);
    float detail = fbm(flow * 1.83 + vec3(-Time * 0.72, Time * 0.39, Time * 0.51));
    float flameBand = sin(surface.y * 12.0 - Time * 5.1 + coarse * 5.4) * 0.13;
    float density = smoothstep(0.34, 0.82, coarse * 0.72 + detail * 0.38 + flameBand);
    float whiteCore = smoothstep(0.86, 0.18, impact) * smoothstep(0.31, 0.86, detail + Heat * 0.14);

    vec3 emberRed = vec3(0.68, 0.018, 0.001);
    vec3 moltenOrange = vec3(1.0, 0.16, 0.008);
    vec3 hotGold = vec3(1.0, 0.72, 0.16);
    vec3 whiteHot = vec3(1.0, 0.985, 0.82);
    vec3 color = mix(emberRed, moltenOrange, clamp(density * 0.82 + center * 0.34, 0.0, 1.0));
    color = mix(color, hotGold, clamp(whiteCore * 0.70 + Heat * 0.15, 0.0, 0.82));
    color = mix(color, whiteHot, clamp((Heat - 1.0) * 0.58 + whiteCore * Heat * 0.36, 0.0, 0.91));
    color *= 0.82 + center * 0.72 + density * 0.52 + Heat * 0.23;

    float edgeMask = smoothstep(1.03, 0.87, impact);
    float hotSolid = smoothstep(1.55, 2.8, Heat);
    float alpha = (
        0.075 +
        thickness * 0.075 +
        density * 0.23 +
        whiteCore * 0.14 +
        rim * 0.17 +
        hotSolid * (0.22 + center * 0.18)
    ) * edgeMask * Opacity;
    if (alpha < 0.004) discard;
    fragColor = vec4(color * vertexColor.rgb, clamp(alpha * vertexColor.a, 0.0, 0.96));
}
