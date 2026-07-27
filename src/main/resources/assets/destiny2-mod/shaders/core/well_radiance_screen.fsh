#version 150

uniform float Strength;
uniform float Time;

in vec2 screenUv;

out vec4 fragColor;

void main() {
    float leftEdge = screenUv.x;
    float rightEdge = 1.0 - screenUv.x;
    float topEdge = screenUv.y;
    float bottomEdge = 1.0 - screenUv.y;

    float sideDistance = min(leftEdge, rightEdge);
    float sideGlow = 1.0 - smoothstep(0.0, 0.255, sideDistance);
    float bottomGlow = 1.0 - smoothstep(0.0, 0.285, bottomEdge);
    float topGlow = (1.0 - smoothstep(0.0, 0.180, topEdge)) * 0.58;
    float broadGlow = max(max(sideGlow, bottomGlow), topGlow);

    float nearestEdge = min(min(leftEdge, rightEdge), min(topEdge, bottomEdge));
    float edgeCore = 1.0 - smoothstep(0.0, 0.052, nearestEdge);
    float cornerBloom = sideGlow * max(bottomGlow, topGlow);
    float pulse = 0.96 + 0.04 * sin(Time * 1.35);

    vec3 paleGold = vec3(1.0, 0.93, 0.68);
    vec3 warmWhite = vec3(1.0, 0.99, 0.88);
    vec3 color = mix(
        paleGold,
        warmWhite,
        clamp(edgeCore * 0.72 + cornerBloom * 0.38, 0.0, 1.0)
    );

    float alpha = (
        broadGlow * 0.165 +
        edgeCore * 0.085 +
        cornerBloom * 0.055
    ) * pulse * Strength;

    if (alpha < 0.001) discard;
    fragColor = vec4(color, clamp(alpha, 0.0, 0.31));
}
