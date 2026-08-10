#version 150

uniform float Time;
uniform float Intensity;
uniform float Formation;
uniform float Seed;

in vec2 barrierUv;
in vec4 vertexColor;

out vec4 fragColor;

float hash21(vec2 point) {
    return fract(sin(dot(point, vec2(127.1, 311.7)) + Seed * 41.3) * 43758.5453);
}

float valueNoise(vec2 point) {
    vec2 cell = floor(point);
    vec2 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);
    return mix(
        mix(hash21(cell), hash21(cell + vec2(1.0, 0.0)), local.x),
        mix(hash21(cell + vec2(0.0, 1.0)), hash21(cell + vec2(1.0, 1.0)), local.x),
        local.y
    );
}

void main() {
    vec2 uv = barrierUv;
    vec2 warped = uv;
    warped.x += sin(uv.y * 8.0 + Time * 0.75) * 0.012;
    warped.y += sin(uv.x * 10.0 - Time * 0.55) * 0.010;

    float fineCloud = valueNoise(warped * vec2(8.0, 6.0) + Time * vec2(0.08, -0.11));
    float broadCloud = valueNoise(warped * vec2(3.2, 4.5) - Time * vec2(0.035, 0.07));
    float slowCloud = valueNoise(warped * vec2(1.7, 2.3) + vec2(Time * 0.022, Seed * 7.0));

    // The charged barricade behaves like a sheet of electrically lit water.
    // Several soft, domain-warped crests travel from the centre to the rim;
    // none of these layers forms a literal lightning bolt.
    vec2 fromCentre = vec2((uv.x - 0.5) * 1.32, uv.y - 0.50);
    float radialDistance = length(fromCentre);
    float radialAngle = atan(fromCentre.y, fromCentre.x);
    float radialWarp = radialDistance + (broadCloud - 0.5) * 0.115;
    radialWarp += sin(radialAngle * 3.0 + Time * 0.38 + slowCloud * 3.2) * 0.026;
    radialWarp += sin(uv.x * 8.0 - uv.y * 6.0 - Time * 0.52) * 0.018;

    float waterWaveA = 0.5 + 0.5 * sin(radialWarp * 27.0 - Time * 2.55);
    float waterWaveB = 0.5 + 0.5 * sin(
        radialWarp * 39.0 - Time * 3.45 + fineCloud * 4.2 + sin(radialAngle * 5.0) * 0.55
    );
    float waterWaveC = 0.5 + 0.5 * sin(
        radialWarp * 18.0 - Time * 1.72 + broadCloud * 5.8 - sin(uv.y * 9.0 + Time * 0.4)
    );
    float broadWaterCrest = smoothstep(0.30, 0.92, waterWaveA);
    float foldingWaterCrest = smoothstep(0.52, 0.97, waterWaveB);
    float slowWaterBody = smoothstep(0.22, 0.90, waterWaveC);
    float outwardEnergy = smoothstep(
        0.11,
        0.58,
        radialDistance + (broadCloud - 0.5) * 0.055
    );
    float liquidFlow = clamp(
        broadWaterCrest * 0.50 + foldingWaterCrest * 0.27 + slowWaterBody * 0.32,
        0.0,
        1.0
    );
    liquidFlow *= outwardEnergy;

    // A dense electrical network crawls across the wet membrane. Each current
    // has a thin white conducting core, a wider blue wake, broken sections and
    // a secondary branch. Fast pulses run from the centre towards both sides;
    // the liquid field only drags and softens their paths.
    float outwardX = abs(uv.x - 0.5) * 2.0;
    float sideId = uv.x < 0.5 ? -1.0 : 1.0;
    float currentGlow = 0.0;
    float currentCrest = 0.0;
    for (int currentIndex = 0; currentIndex < 8; currentIndex++) {
        float index = float(currentIndex);
        float identity = index * 17.13 + Seed * 9.71 + sideId * 3.17;
        float anchorY = 0.10 + hash21(vec2(identity, index + 2.0)) * 0.80;

        float coarseBend = sin(outwardX * (5.8 + index * 0.31) + identity + Time * (0.28 + index * 0.018));
        float sharpBend = sin(outwardX * (16.0 + index * 0.73) - identity * 1.7 - Time * (0.62 + index * 0.025));
        float wetDrag = valueNoise(vec2(outwardX * (8.5 + index * 0.23) + identity, Time * 0.72 + index * 5.3));
        float currentY = anchorY + coarseBend * 0.052 + sharpBend * 0.024;
        currentY += (wetDrag - 0.5) * 0.115 + (broadCloud - 0.5) * 0.032;

        float forkStart = 0.16 + hash21(vec2(identity + 4.0, index)) * 0.38;
        float forkGrowth = max(0.0, outwardX - forkStart);
        float forkDirection = hash21(vec2(identity + 8.0, index + 1.0)) < 0.5 ? -1.0 : 1.0;
        float forkY = currentY + forkDirection * forkGrowth * (0.085 + index * 0.004);
        forkY += sin(outwardX * 23.0 + identity * 2.1 - Time * 1.35) * 0.016;

        float mainDistance = abs(uv.y - currentY);
        float forkDistance = abs(uv.y - forkY);
        float forkEnabled = smoothstep(forkStart, forkStart + 0.055, outwardX);
        float wireDistance = min(mainDistance, mix(0.20, forkDistance, forkEnabled));

        // Keep the interruption mask continuous in both axes. Quantising Y
        // here used to divide the membrane into visible rectangular bands.
        float sectionNoise = valueNoise(vec2(
            outwardX * 18.0 - Time * 2.15 + sin(uv.y * 9.0 + identity) * 0.72,
            identity + uv.y * 5.6 + sin(outwardX * 7.0 - Time * 0.38) * 0.31
        ));
        float brokenSections = smoothstep(0.31, 0.56, sectionNoise + sin(outwardX * 38.0 + identity) * 0.12);
        float runningPulse = 0.5 + 0.5 * sin(outwardX * 24.0 - Time * (8.0 + index * 0.18) + identity);
        runningPulse = 0.24 + smoothstep(0.36, 0.92, runningPulse) * 0.92;

        float wirePixelWidth = max(fwidth(wireDistance), 0.0008);
        float wireWake = 1.0 - smoothstep(0.008, 0.038 + wirePixelWidth * 1.5, wireDistance);
        float wireCore = 1.0 - smoothstep(0.0012, 0.0072 + wirePixelWidth, wireDistance);
        currentGlow = max(currentGlow, wireWake * brokenSections * runningPulse);
        currentCrest = max(currentCrest, wireCore * brokenSections * runningPulse);
    }
    float currentOutward = smoothstep(0.035, 0.72, outwardX + (broadCloud - 0.5) * 0.10);
    float currentVerticalFade = smoothstep(0.02, 0.075, uv.y) * smoothstep(0.02, 0.075, 1.0 - uv.y);
    currentGlow *= currentOutward * currentVerticalFade * (0.78 + liquidFlow * 0.30);
    currentCrest *= currentOutward * currentVerticalFade * (0.72 + liquidFlow * 0.38);
    float horizontalFlow = 0.5 + 0.5 * sin(
        warped.y * 17.0 + broadCloud * 4.2 + sin(warped.x * 7.0) * 0.8 - Time * 0.72
    );
    horizontalFlow = smoothstep(0.30, 0.92, horizontalFlow);

    float horizontalEdge = min(uv.x, 1.0 - uv.x);
    float verticalEdge = min(uv.y, 1.0 - uv.y);
    float edgeDistance = min(horizontalEdge, verticalEdge);
    float edgeBreakup = (fineCloud - 0.5) * 0.032 +
        sin(uv.x * 34.0 + uv.y * 19.0 - Time * 1.7) * 0.006;
    float distortedEdge = edgeDistance + edgeBreakup;
    float rimCore = 1.0 - smoothstep(0.0, 0.036, distortedEdge);
    float rimMass = 1.0 - smoothstep(0.012, 0.165, distortedEdge);
    float sideMass = 1.0 - smoothstep(0.0, 0.19, horizontalEdge);
    float topMass = 1.0 - smoothstep(0.0, 0.13, 1.0 - uv.y);
    float baseMass = 1.0 - smoothstep(0.0, 0.12, uv.y);
    float rollingHighlight = pow(max(0.0, sin(uv.y * 12.0 + broadCloud * 5.0 - Time * 0.65)), 7.0);
    rollingHighlight *= 0.35 + sideMass * 0.65;
    float energyCells = smoothstep(0.53, 0.84, broadCloud * 0.68 + fineCloud * 0.34 + liquidFlow * 0.24);
    energyCells *= outwardEnergy * (0.50 + sideMass * 0.32 + rimMass * 0.28);
    float lowerBandCentre = 0.17 + (broadCloud - 0.5) * 0.055;
    float lowerEnergyBand = 1.0 - smoothstep(0.035, 0.20, abs(uv.y - lowerBandCentre));
    lowerEnergyBand *= 0.52 + horizontalFlow * 0.48;
    // One broad, low-opacity reflection slowly crosses the clear centre. It
    // suggests curved glass without sampling or distorting the world behind it.
    float sheenPosition = fract(Time * 0.018 + Seed * 0.37) * 1.70 - 0.35;
    float sheenDistance = abs((uv.x + uv.y * 0.28 + (broadCloud - 0.5) * 0.055) - sheenPosition);
    float glassSheen = 1.0 - smoothstep(0.035, 0.17, sheenDistance);
    glassSheen *= smoothstep(0.08, 0.26, horizontalEdge) * smoothstep(0.05, 0.22, verticalEdge);

    float castFlash = (1.0 - smoothstep(0.0, 1.0, Formation)) * 0.58;
    float centreClarity = 0.58 + sideMass * 0.42 + max(topMass, baseMass) * 0.25;
    float body = (0.046 + broadCloud * 0.070 + fineCloud * 0.034 + horizontalFlow * 0.052) * centreClarity;
    float milkyVolume = rimMass * (0.15 + slowCloud * 0.15) + sideMass * horizontalFlow * 0.13;
    float alpha = (
        body + milkyVolume + liquidFlow * 0.105 + currentGlow * 0.145 + currentCrest * 0.090 +
        rollingHighlight * 0.060 + energyCells * 0.11 +
        lowerEnergyBand * 0.095 + glassSheen * 0.032 + rimCore * 0.48 +
        topMass * 0.095 + baseMass * 0.14 + castFlash
    ) * Intensity;

    vec3 deepArc = vec3(0.20, 0.55, 0.88);
    vec3 arcBlue = vec3(0.48, 0.78, 1.0);
    vec3 paleCore = vec3(0.86, 0.96, 1.0);
    float core = clamp(
        rimCore * 0.94 + rimMass * 0.38 + liquidFlow * 0.32 + currentGlow * 0.44 +
        currentCrest * 0.68 + rollingHighlight * 0.14 + energyCells * 0.28 +
        lowerEnergyBand * 0.20 + glassSheen * 0.22 + castFlash,
        0.0,
        1.0
    );
    vec3 color = mix(deepArc, arcBlue, broadCloud * 0.48 + slowCloud * 0.20 + 0.18);
    color = mix(color, paleCore, core);

    alpha = clamp(alpha, 0.0, 0.78);
    if (alpha < 0.008) discard;
    fragColor = vec4(color * vertexColor.rgb, alpha * vertexColor.a);
}
