#version 150

uniform vec3 CameraPos;
uniform float Time;
uniform float Opacity;

in vec3 spherePos;
in vec4 vertexColor;

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
    for (int i = 0; i < 4; ++i) {
        value += noise(p) * amplitude;
        p = p * 2.04 + vec3(5.1, -7.3, 9.7);
        amplitude *= 0.47;
    }
    return value;
}

void main() {
    vec3 surface = normalize(spherePos);
    float rotation = Time * 0.72;
    mat2 turn = mat2(cos(rotation), -sin(rotation), sin(rotation), cos(rotation));
    vec3 flowing = surface;
    flowing.xz = turn * flowing.xz;
    flowing.y -= Time * 0.92;

    float broad = fbm(flowing * 2.65 + vec3(Time * 0.18, -Time * 0.34, Time * 0.13));
    float detail = fbm(flowing * 5.4 + vec3(-Time * 0.41, Time * 0.72, Time * 0.29));
    float longitude = atan(surface.z, surface.x);
    float flameWave = sin(
        surface.y * 13.0 +
        longitude * 3.0 -
        Time * 7.2 +
        broad * 6.0
    ) * 0.17;
    float flowingFlame = smoothstep(0.38, 0.79, broad * 0.68 + detail * 0.43 + flameWave);
    float brokenVeins = smoothstep(0.20, 0.58, abs(broad - detail));

    vec3 viewDirection = normalize(CameraPos - surface);
    float fresnel = pow(1.0 - clamp(abs(dot(surface, viewDirection)), 0.0, 1.0), 1.65);
    float alpha = 0.025 + flowingFlame * 0.092 + detail * 0.026;
    alpha += fresnel * (0.072 + flowingFlame * 0.078);
    alpha *= (1.0 - brokenVeins * 0.34) * Opacity;
    if (alpha < 0.004) discard;

    vec3 amberGold = vec3(1.0, 0.22, 0.012);
    vec3 solarGold = vec3(1.0, 0.56, 0.075);
    vec3 paleGold = vec3(1.0, 0.88, 0.42);
    vec3 color = mix(amberGold, solarGold, broad);
    color = mix(color, paleGold, clamp(flowingFlame * 0.64 + fresnel * 0.30, 0.0, 0.76));
    color *= 1.02 + flowingFlame * 0.28;
    fragColor = vec4(color * vertexColor.rgb, clamp(alpha * vertexColor.a, 0.0, 0.30));
}
