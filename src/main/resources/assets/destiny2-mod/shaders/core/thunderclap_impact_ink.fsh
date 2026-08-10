#version 150

uniform float Strength;
uniform float Seed;
uniform float Aspect;

in vec2 screenUv;
out vec4 fragColor;

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float lineSegment(vec2 p, vec2 a, vec2 b, float width) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return 1.0 - smoothstep(width, width * 1.75, length(pa - ba * h));
}

void main() {
    vec2 p = screenUv - vec2(0.5, 0.56);
    p.x *= Aspect;
    float r = length(p);
    float a = atan(p.y, p.x);
    float seed = floor(Seed);

    // Imperfect off-white paper and photocopied grain.
    float fineGrain = hash21(floor(screenUv * vec2(520.0, 300.0)) + seed);
    float broadGrain = hash21(floor(screenUv * vec2(92.0, 54.0)) + seed * 0.37);
    vec3 paper = vec3(0.965, 0.958, 0.925) * (0.91 + fineGrain * 0.09 + broadGrain * 0.025);

    // Broken radial brush rays. Angular cells make every activation a fixed,
    // authored-looking drawing rather than animated TV noise.
    float cellCount = 72.0;
    float cell = floor((a / 6.2831853 + 0.5) * cellCount);
    float angular = fract((a / 6.2831853 + 0.5) * cellCount) - 0.5;
    float rayRandom = hash11(cell + seed * 1.71);
    float rayStart = 0.12 + hash11(cell * 4.17 + seed) * 0.34;
    float rayEnd = rayStart + 0.12 + hash11(cell * 2.31 + seed * 2.0) * 0.72;
    float rayWidth = mix(0.045, 0.22, hash11(cell * 8.13 + seed));
    float rays = (1.0 - smoothstep(rayWidth, rayWidth + 0.035, abs(angular)))
        * step(0.38, rayRandom) * smoothstep(rayStart, rayStart + 0.025, r)
        * (1.0 - smoothstep(rayEnd - 0.035, rayEnd, r));

    // Dry-brush breakup cuts holes through otherwise clean radial marks.
    float dry = hash21(vec2(cell, floor(r * 110.0)) + seed);
    rays *= smoothstep(0.18, 0.60, dry + rayRandom * 0.38);

    // Jagged black impact mass with white-hot negative space in its centre.
    float jagged = hash11(floor(a * 17.0) + seed * 3.0) * 0.085;
    float burst = 1.0 - smoothstep(0.14 + jagged, 0.20 + jagged, r);
    float whiteCore = 1.0 - smoothstep(0.035, 0.115, r);
    burst *= 1.0 - whiteCore;

    // Rough perspective strokes suggest the Minecraft ground being redrawn.
    float ground = 0.0;
    for (int i = 0; i < 9; ++i) {
        float fi = float(i);
        float x = mix(-1.25, 1.25, hash11(fi * 9.7 + seed));
        ground = max(ground, lineSegment(p, vec2(0.0, 0.08), vec2(x, 0.64), 0.004 + hash11(fi + seed) * 0.008));
    }
    for (int i = 0; i < 5; ++i) {
        float y = 0.14 + float(i) * 0.105;
        float wobble = (hash11(float(i) + seed) - 0.5) * 0.035;
        ground = max(ground, lineSegment(p, vec2(-1.25, y + wobble), vec2(1.25, y - wobble), 0.0035));
    }

    // Hand-inked slash accents, deliberately asymmetric.
    float slash = 0.0;
    slash = max(slash, lineSegment(p, vec2(-0.64, -0.42), vec2(-0.10, -0.06), 0.012));
    slash = max(slash, lineSegment(p, vec2(0.12, -0.08), vec2(0.82, -0.48), 0.008));
    slash = max(slash, lineSegment(p, vec2(-0.44, 0.47), vec2(-0.08, 0.13), 0.007));

    float ink = clamp(rays * 0.92 + burst + ground * 0.72 + slash, 0.0, 1.0);
    // A few charcoal flecks keep the image from reading as vector geometry.
    float flecks = step(0.988, fineGrain) * smoothstep(0.10, 0.82, r);
    ink = max(ink, flecks * 0.68);

    vec3 color = mix(paper, vec3(0.008, 0.009, 0.011), ink);
    color = mix(color, vec3(1.0), whiteCore * 0.96);
    float alpha = clamp(Strength * 0.97, 0.0, 0.97);
    fragColor = vec4(color, alpha);
}
