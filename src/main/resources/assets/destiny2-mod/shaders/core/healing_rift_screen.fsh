#version 150

uniform float Strength;
uniform float Time;
uniform float Aspect;

in vec2 screenUv;

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

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.60;
    for (int i = 0; i < 3; ++i) {
        value += noise(p) * amplitude;
        p = p * 1.87 + vec2(5.7, -4.3);
        amplitude *= 0.42;
    }
    return value;
}

float expandingFog(vec2 centred, float phase, vec2 offset) {
    // Each layer grows from the centre towards the viewport edge. Its opacity
    // reaches zero before the phase wraps, so the animation has no reset seam.
    float expansion = mix(0.52, 1.62, phase);
    float life = sin(phase * 3.14159265);
    vec2 p = centred * (3.55 / expansion);
    p += offset;
    p += vec2(
        sin(p.y * 0.82 + Time * 0.17),
        cos(p.x * 0.71 - Time * 0.14)
    ) * 0.24;
    return fbm(p) * life * life;
}

void main() {
    vec2 centred = (screenUv - 0.5) * vec2(Aspect, 1.0);
    float radial = length(centred);

    float nearestEdge = min(
        min(screenUv.x, 1.0 - screenUv.x),
        min(screenUv.y, 1.0 - screenUv.y)
    );
    float edgeFog = 1.0 - smoothstep(0.018, 0.43, nearestEdge);
    float edgeCore = 1.0 - smoothstep(0.0, 0.075, nearestEdge);

    float cycle = Time * 0.42;
    float phaseA = fract(cycle);
    float phaseB = fract(cycle + 0.5);
    float fogA = expandingFog(centred, phaseA, vec2(2.7, -1.9));
    float fogB = expandingFog(centred, phaseB, vec2(-3.8, 4.6));
    float broadDrift = fbm(
        centred * 2.15 +
        vec2(Time * 0.075, -Time * 0.052) +
        vec2(7.1, 3.4)
    );
    float fog = smoothstep(
        0.12,
        0.58,
        clamp(max(fogA, fogB) * 0.78 + broadDrift * 0.22, 0.0, 1.0)
    );

    // Keep the centre clean. The previous normalized radial direction became
    // unstable here and produced the vertical noise visible above and below
    // the crosshair.
    float cleanCentre = smoothstep(0.10, 0.34, radial);
    fog *= cleanCentre;

    float cornerBloom =
        (1.0 - smoothstep(0.0, 0.22, min(screenUv.x, 1.0 - screenUv.x))) *
        (1.0 - smoothstep(0.0, 0.25, min(screenUv.y, 1.0 - screenUv.y)));

    vec3 softBlue = vec3(0.31, 0.56, 1.0);
    vec3 iceWhite = vec3(0.78, 0.92, 1.0);
    vec3 color = mix(
        softBlue,
        iceWhite,
        clamp(fog * 0.58 + edgeCore * 0.42, 0.0, 1.0)
    );

    float alpha = (
        edgeFog * (0.075 + fog * 0.225) +
        edgeCore * 0.072 +
        cornerBloom * 0.035
    ) * Strength;

    if (alpha < 0.001) discard;
    fragColor = vec4(color, clamp(alpha, 0.0, 0.38));
}
