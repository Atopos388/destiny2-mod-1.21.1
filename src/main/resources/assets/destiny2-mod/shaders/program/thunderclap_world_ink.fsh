#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Strength;
uniform float Seed;
uniform float PlatePhase;
uniform float PhaseProgress;
uniform float PlateIndex;
uniform vec2 ImpactCenter;
uniform vec2 ImpactDirection;
uniform float BurstStrength;
uniform float CopyOnly;

in vec2 texCoord;
out vec4 fragColor;

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

float hash11(float p) {
    return fract(sin(p * 127.1) * 43758.5453);
}

float noise1(float x) {
    float cell = floor(x);
    float blend = fract(x);
    blend = blend * blend * (3.0 - 2.0 * blend);
    return mix(hash11(cell), hash11(cell + 1.0), blend);
}

vec2 rotate2(vec2 p, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, -s, s, c) * p;
}

float outwardWedge(vec2 p, vec2 direction, float angle, float extent, float width, float seed) {
    vec2 d = rotate2(direction, angle);
    vec2 q = vec2(dot(p, d), dot(p, vec2(-d.y, d.x)));
    float along = q.x;
    float inside = smoothstep(-0.025, 0.018, along) * (1.0 - smoothstep(extent * 0.82, extent, along));
    float t = clamp(along / max(extent, 0.001), 0.0, 1.0);
    float torn = sin(along * 37.0 + seed * 1.7) * 0.010
        + sin(along * 83.0 - seed * 0.9) * 0.004;
    float halfWidth = mix(0.012, width, pow(t, 0.72))
        * mix(0.76, 1.18, noise1(along * 13.0 + seed));
    float body = 1.0 - smoothstep(halfWidth * 0.76, halfWidth, abs(q.y + torn));
    return body * inside;
}

float inkBlade(vec2 p, vec2 direction, float angle, float extent, float width, float seed) {
    vec2 d = rotate2(direction, angle);
    vec2 q = vec2(dot(p, d), dot(p, vec2(-d.y, d.x)));
    float along = abs(q.x);
    float taper = width * mix(1.0, 0.08, smoothstep(0.08, extent, along));
    float broken = step(0.22, noise1(q.x * 18.0 + seed));
    return (1.0 - smoothstep(taper * 0.62, taper, abs(q.y)))
        * (1.0 - smoothstep(extent * 0.78, extent, along))
        * mix(0.64, 1.0, broken);
}

void main() {
    vec4 source = texture(DiffuseSampler, texCoord);
    if (CopyOnly > 0.5) {
        fragColor = source;
        return;
    }

    vec2 texel = 1.0 / max(OutSize, vec2(1.0));
    float centerLum = luminance(source.rgb);
    float toneBlur = (
        centerLum * 4.0
        + luminance(texture(DiffuseSampler, texCoord + vec2( 5.0,  0.0) * texel).rgb)
        + luminance(texture(DiffuseSampler, texCoord + vec2(-5.0,  0.0) * texel).rgb)
        + luminance(texture(DiffuseSampler, texCoord + vec2( 0.0,  5.0) * texel).rgb)
        + luminance(texture(DiffuseSampler, texCoord + vec2( 0.0, -5.0) * texel).rgb)
    ) * 0.125;

    vec2 edgeStep = texel * 3.5;
    float tl = luminance(texture(DiffuseSampler, texCoord + edgeStep * vec2(-1.0, -1.0)).rgb);
    float tc = luminance(texture(DiffuseSampler, texCoord + edgeStep * vec2( 0.0, -1.0)).rgb);
    float tr = luminance(texture(DiffuseSampler, texCoord + edgeStep * vec2( 1.0, -1.0)).rgb);
    float ml = luminance(texture(DiffuseSampler, texCoord + edgeStep * vec2(-1.0,  0.0)).rgb);
    float mr = luminance(texture(DiffuseSampler, texCoord + edgeStep * vec2( 1.0,  0.0)).rgb);
    float bl = luminance(texture(DiffuseSampler, texCoord + edgeStep * vec2(-1.0,  1.0)).rgb);
    float bc = luminance(texture(DiffuseSampler, texCoord + edgeStep * vec2( 0.0,  1.0)).rgb);
    float br = luminance(texture(DiffuseSampler, texCoord + edgeStep * vec2( 1.0,  1.0)).rgb);
    float gx = -tl - 2.0 * ml - bl + tr + 2.0 * mr + br;
    float gy = -tl - 2.0 * tc - tr + bl + 2.0 * bc + br;
    float broadEdge = smoothstep(0.15, 0.52, length(vec2(gx, gy)));

    float aspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 p = (texCoord - ImpactCenter) * vec2(aspect, 1.0);
    vec2 direction = normalize(vec2(ImpactDirection.x * aspect, ImpactDirection.y) + vec2(0.0001, 0.0));

    // Four hard-cut compositions. Every plate has a different balance of
    // positive and negative space, but all converge on the projected fist/core.
    float wedgeA = outwardWedge(p, direction,  2.45, 1.35, 0.34, Seed + 1.0);
    float wedgeB = outwardWedge(p, direction, -2.05, 1.20, 0.25, Seed + 4.0);
    float wedgeC = outwardWedge(p, direction,  0.72, 1.05, 0.18, Seed + 8.0);
    float wedgeD = outwardWedge(p, direction, -0.48, 1.25, 0.29, Seed + 12.0);
    float bladeA = inkBlade(p, direction, 0.10, 1.10, 0.055, Seed + 15.0);
    float bladeB = inkBlade(p, direction, 1.34, 0.92, 0.038, Seed + 19.0);
    float energySilhouette = smoothstep(0.46, 0.72, toneBlur);
    float impactPin = 1.0 - smoothstep(0.025, 0.12, length(p));

    float plate0 = max(max(wedgeA, wedgeC), max(bladeA, energySilhouette * 0.76));
    float plate1 = 1.0 - max(max(wedgeB, wedgeD), bladeB);
    plate1 = max(plate1, energySilhouette * 0.92 + impactPin * 0.72);
    float plate2 = max(max(wedgeB, bladeA), max(bladeB, impactPin));
    float plate3 = 1.0 - max(max(wedgeA, wedgeD), max(bladeA, bladeB));
    plate3 = mix(plate3, max(plate3, energySilhouette), 0.72);

    float select0 = 1.0 - step(0.5, PlateIndex);
    float select1 = step(0.5, PlateIndex) * (1.0 - step(1.5, PlateIndex));
    float select2 = step(1.5, PlateIndex) * (1.0 - step(2.5, PlateIndex));
    float select3 = step(2.5, PlateIndex);
    float abstractPlate = plate0 * select0 + plate1 * select1 + plate2 * select2 + plate3 * select3;
    abstractPlate = max(abstractPlate, BurstStrength * (1.0 - smoothstep(0.0, 0.48, length(p))));

    // The middle hold removes Minecraft's texture chatter while keeping broad
    // world silhouettes and the player readable on clean paper white.
    float paperScene = clamp(1.0 - broadEdge * 0.90, 0.0, 1.0);
    float subjectLift = smoothstep(0.42, 0.70, toneBlur) * 0.16;
    paperScene = max(paperScene, subjectLift);

    // Ink arrives from the frame boundary after the paper hold. Low-frequency
    // lobes make it asymmetrical; no uniform circular vignette is visible.
    float radial = length(p * vec2(0.82, 1.0));
    float angularLobes = sin(atan(p.y, p.x) * 3.0 + Seed * 0.7) * 0.08
        + sin(atan(p.y, p.x) * 5.0 - Seed) * 0.035;
    float inkFront = mix(0.94, 0.16, PhaseProgress) + angularLobes;
    float closingInk = smoothstep(inkFront - 0.12, inkFront + 0.04, radial);
    closingInk = max(closingInk, outwardWedge(p, direction, 2.85, 1.45, 0.46, Seed + 27.0) * PhaseProgress);
    float closePlate = paperScene * (1.0 - closingInk);
    closePlate = max(closePlate, impactPin * (1.0 - PhaseProgress) * 0.84);

    float phaseAbstract = 1.0 - step(0.5, PlatePhase);
    float phasePaper = step(0.5, PlatePhase) * (1.0 - step(1.5, PlatePhase));
    float phaseClose = step(1.5, PlatePhase);
    float tone = abstractPlate * phaseAbstract + paperScene * phasePaper + closePlate * phaseClose;
    vec3 inkWorld = vec3(clamp(tone, 0.0, 1.0));
    fragColor = vec4(mix(source.rgb, inkWorld, clamp(Strength, 0.0, 1.0)), source.a);
}
