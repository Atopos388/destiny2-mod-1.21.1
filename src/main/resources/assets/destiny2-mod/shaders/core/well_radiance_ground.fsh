#version 150

uniform float Time;
uniform float Intensity;
uniform float Radius;

in vec4 vertexColor;
in vec3 groundPos;

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
    float amplitude = 0.57;
    for (int i = 0; i < 3; ++i) {
        value += noise(p) * amplitude;
        p = p * 1.92 + vec3(7.1, 11.7, 5.3);
        amplitude *= 0.43;
    }
    return value;
}

void main() {
    vec2 localPosition = groundPos.xz;
    float radial = length(localPosition);
    if (radial > 1.0) discard;

    vec2 worldPosition = localPosition * Radius;

    // Broad domain-warped cloud banks form the surface. There are deliberately
    // no angular waves here, because those always resolve into radial spokes.
    vec2 drift = vec2(Time * 0.055, -Time * 0.032);
    float warpX = fbm(vec3(
        worldPosition * 0.095 + drift,
        Time * 0.025
    ));
    float warpY = fbm(vec3(
        worldPosition * 0.083 - drift + vec2(6.7, -4.1),
        -Time * 0.021
    ));
    vec2 warpedPosition =
        worldPosition * 0.115 +
        vec2(warpX - 0.5, warpY - 0.5) * 0.72 +
        drift;

    float cloudA = fbm(vec3(warpedPosition, Time * 0.018));
    float cloudB = fbm(vec3(
        warpedPosition * 0.68 + vec2(-3.8, 5.6),
        -Time * 0.014
    ));
    float cloudField = clamp(cloudA * 0.72 + cloudB * 0.28, 0.0, 1.0);
    float cloud = smoothstep(0.34, 0.76, cloudField);

    // This is only a broad brightness drift, not a visible stripe pattern.
    float flow = 0.5 + 0.5 * sin(
        worldPosition.x * 0.105 -
        worldPosition.y * 0.068 -
        Time * 0.23 +
        (warpX - warpY) * 2.1
    );
    flow = smoothstep(0.18, 0.88, flow);

    float interiorEdge = smoothstep(1.0, 0.88, radial);
    float centreGlow = 1.0 - smoothstep(0.0, 0.30, radial);

    // The reference is defined by a complete, clean and very thin outer ring.
    float rimDistance = abs(radial - 0.987);
    float rim = 1.0 - smoothstep(0.004, 0.012, rimDistance);

    vec3 mistColor = vec3(0.82, 0.91, 0.87);
    vec3 cloudColor = vec3(0.96, 0.985, 0.91);
    vec3 rimColor = vec3(1.0, 0.97, 0.72);
    vec3 interiorColor = mix(
        mistColor,
        cloudColor,
        clamp(cloud * 0.72 + flow * 0.16 + centreGlow * 0.18, 0.0, 1.0)
    );
    vec3 color = mix(interiorColor, rimColor, rim);

    float interiorAlpha =
        (0.052 + cloud * 0.130 + flow * 0.032 + centreGlow * 0.045) *
        interiorEdge;
    float alpha = max(interiorAlpha, rim * 0.64);
    alpha *= Intensity * vertexColor.a;

    if (alpha < 0.005) discard;
    fragColor = vec4(color * vertexColor.rgb, clamp(alpha, 0.0, 0.68));
}
