#version 150

uniform float Time;
uniform float Intensity;
uniform float Radius;
uniform float Layer;

in vec4 vertexColor;
in vec3 groundPos;

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
    float amplitude = 0.58;
    for (int i = 0; i < 4; ++i) {
        value += noise(p) * amplitude;
        p = p * 1.93 + vec2(6.4, -4.7);
        amplitude *= 0.44;
    }
    return value;
}

void main() {
    vec2 localPosition = groundPos.xz;
    float radial = length(localPosition);
    if (radial > 1.0) discard;

    vec2 worldPosition = localPosition * Radius;
    float angle = atan(localPosition.y, localPosition.x);
    vec2 drift = vec2(Time * 0.038 + Layer * 0.31, -Time * 0.026 - Layer * 0.19);

    float warpX = fbm(worldPosition * 0.27 + drift);
    float warpY = fbm(worldPosition * 0.23 - drift + vec2(4.8, -3.1));
    vec2 warpedPosition =
        worldPosition * 0.34 +
        vec2(warpX - 0.5, warpY - 0.5) * 0.86;

    float cloudA = fbm(warpedPosition + drift);
    float cloudB = fbm(warpedPosition * 0.63 - drift + vec2(-5.2, 2.7));
    float cloud = smoothstep(
        0.30,
        0.78,
        clamp(cloudA * 0.72 + cloudB * 0.28, 0.0, 1.0)
    );

    float centre = 1.0 - smoothstep(0.01, 0.30, radial);
    centre *= centre;

    // Reverse-advection along the local radial direction makes a continuous
    // body of fog travel from the source to the boundary. There are no phase
    // thresholds or periodic fronts, so this cannot become a set of rings.
    vec2 radialDirection = localPosition / max(radial, 0.025);
    vec2 radialTangent = vec2(-radialDirection.y, radialDirection.x);
    float advectedRadius = radial * Radius - Time * 1.35;
    vec2 flowPosition =
        radialDirection * advectedRadius * 0.42 +
        radialTangent * ((warpX - warpY) * 0.82) +
        vec2(Layer * 0.37, -Layer * 0.23);
    float outwardCloudA = fbm(flowPosition + vec2(2.6, -1.9));
    float outwardCloudB = fbm(
        flowPosition * 0.66 +
        vec2(-4.1, 5.3) +
        radialDirection * 0.45
    );
    float outwardCloud = smoothstep(
        0.27,
        0.78,
        clamp(outwardCloudA * 0.72 + outwardCloudB * 0.28, 0.0, 1.0)
    );
    float angularBreakup = 0.58 + 0.42 * fbm(
        radialDirection * 1.75 +
        vec2(Time * 0.045, -Time * 0.031)
    );
    float sourceFog = 1.0 - smoothstep(0.0, 0.22, radial);
    float flowingMist = clamp(
        outwardCloud * angularBreakup * 0.72 +
        cloud * 0.28 +
        sourceFog * 0.24,
        0.0,
        1.0
    );

    float ringNoise = noise(
        vec2(cos(angle), sin(angle)) * 3.2 +
        vec2(Time * 0.022, -Time * 0.017)
    );
    float ringRadius = 0.972 + (ringNoise - 0.5) * 0.030;
    float ring = 1.0 - smoothstep(0.004, 0.014, abs(radial - ringRadius));
    ring *= 1.0 - smoothstep(0.02, 0.48, Layer);
    float edgeMist =
        (1.0 - smoothstep(0.72, 1.0, radial)) *
        smoothstep(0.58, 0.90, radial) *
        cloud;
    float interiorEdge = 1.0 - smoothstep(0.94, 1.0, radial);

    vec3 softBlue = vec3(0.67, 0.82, 1.0);
    vec3 paleWhite = vec3(0.94, 0.985, 1.0);
    vec3 color = mix(
        softBlue,
        paleWhite,
        clamp(
            centre * 0.92 +
            flowingMist * 0.68 +
            ring * 0.55,
            0.0,
            1.0
        )
    );

    float interiorAlpha =
        (
            0.040 +
            flowingMist * 0.235 +
            centre * 0.245 +
            edgeMist * 0.075
        ) *
        interiorEdge;
    float alpha = max(interiorAlpha, ring * 0.27);
    alpha *= Intensity * vertexColor.a;

    if (alpha < 0.004) discard;
    fragColor = vec4(color * vertexColor.rgb, clamp(alpha, 0.0, 0.54));
}
