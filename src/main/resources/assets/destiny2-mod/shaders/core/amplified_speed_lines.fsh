#version 150

uniform float Strength;
uniform float Time;
uniform float Aspect;

in vec2 screenUv;

out vec4 fragColor;

float hash11(float value) {
    return fract(sin(value * 127.1) * 43758.5453);
}

float streakLayer(vec2 point, float radius, float angle, float sectors, float speed, float seed) {
    float angularCell = (angle + 3.14159265) / 6.28318530 * sectors;
    float cell = floor(angularCell);
    float positionInCell = fract(angularCell);
    float randomValue = hash11(cell + seed);
    float center = 0.18 + randomValue * 0.64;
    float width = mix(0.045, 0.125, hash11(cell + seed + 19.7));
    float ray = 1.0 - smoothstep(width * 0.55, width * 3.1, abs(positionInCell - center));

    float travel = fract(radius * (2.25 + randomValue * 1.35) - Time * speed + randomValue);
    float head = smoothstep(0.02, 0.14, travel);
    float tail = 1.0 - smoothstep(0.22, 0.62, travel);
    float segment = head * tail;
    float flicker = 0.72 + 0.28 * sin(Time * 10.0 + cell * 2.31);
    return ray * segment * flicker;
}

void main() {
    vec2 point = screenUv - vec2(0.5, 0.47);
    point.x *= Aspect;
    float radius = length(point);
    float angle = atan(point.y, point.x);

    // Keep the sight picture clean; the rush lives in the peripheral field.
    float innerMask = smoothstep(0.43, 0.67, radius);
    float outerMask = 1.0 - smoothstep(1.12, 1.38, radius);
    float edgeBias = smoothstep(0.52, 0.91, radius);

    float fine = streakLayer(point, radius, angle, 92.0, 1.62, 3.0);
    float medium = streakLayer(point, radius, angle + 0.011, 57.0, 1.12, 41.0);
    float broad = streakLayer(point, radius, angle - 0.018, 31.0, 0.78, 83.0);
    float streaks = (fine * 0.46 + medium * 0.38 + broad * 0.25) * innerMask * outerMask;

    float sideDistance = min(screenUv.x, 1.0 - screenUv.x);
    float sideArc = (1.0 - smoothstep(0.0, 0.105, sideDistance)) *
        (0.74 + 0.26 * sin(screenUv.y * 71.0 + Time * 7.0));
    float intensity = (streaks * (0.25 + edgeBias * 0.42) + sideArc * 0.045) * Strength;

    vec3 arcBlue = vec3(0.20, 0.64, 1.0);
    vec3 arcWhite = vec3(0.78, 0.94, 1.0);
    vec3 color = mix(arcBlue, arcWhite, clamp(streaks * 0.92 + sideArc * 0.30, 0.0, 1.0));
    float alpha = clamp(intensity, 0.0, 0.34);
    if (alpha < 0.004) discard;
    fragColor = vec4(color, alpha);
}
