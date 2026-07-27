#version 150

uniform float Time;
uniform float Opacity;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float radial = texCoord.y;
    float phase = texCoord.x * 6.2831853;
    float innerFade = smoothstep(0.0, 0.075, radial);
    float outerFade = 1.0 - smoothstep(0.78, 1.0, radial);
    float edgeEnvelope = innerFade * outerFade;
    float radialBody = 1.0 - smoothstep(0.12, 1.0, radial) * 0.34;

    // Three broad lanes carry independent bright and dark clouds in the same
    // orbital direction. Their differing speeds prevent the disc from reading
    // as one uniformly flashing surface.
    float innerMask = 1.0 - smoothstep(0.18, 0.42, abs(radial - 0.20));
    float middleMask = 1.0 - smoothstep(0.20, 0.46, abs(radial - 0.53));
    float outerMask = 1.0 - smoothstep(0.16, 0.36, abs(radial - 0.84));

    float innerCloud = pow(sin(phase - Time * 15.6) * 0.5 + 0.5, 2.2);
    float middleCloud = pow(sin(phase * 2.0 - Time * 13.2 + 1.7) * 0.5 + 0.5, 2.6);
    float outerCloud = pow(sin(phase * 3.0 - Time * 11.4 + 3.1) * 0.5 + 0.5, 3.0);
    float orbitCloud = (
        innerCloud * innerMask +
        middleCloud * middleMask +
        outerCloud * outerMask
    ) / max(innerMask + middleMask + outerMask, 0.72);

    float turbulence = sin(
        phase * 7.0 - Time * 18.0 + sin(radial * 17.0) * 0.55
    ) * 0.5 + 0.5;
    float movingLight = clamp(orbitCloud * 0.82 + turbulence * 0.18, 0.0, 1.0);
    float innerBrightness = 1.0 - smoothstep(0.0, 0.42, radial);
    float heat = clamp(
        0.08 + innerBrightness * 0.24 + movingLight * 0.68,
        0.0,
        1.0
    );

    vec3 deepViolet = vec3(0.035, 0.002, 0.16);
    vec3 hotViolet = vec3(0.76, 0.28, 1.0);
    vec3 color = mix(deepViolet, hotViolet, heat);
    color *= vertexColor.rgb;

    float bodyLight = 0.45 + movingLight * 0.42;
    float alpha = vertexColor.a * Opacity * edgeEnvelope * radialBody * bodyLight;
    if (alpha < 0.003) discard;
    fragColor = vec4(color, clamp(alpha, 0.0, 0.96));
}
