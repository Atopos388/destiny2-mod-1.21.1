#version 150

uniform vec3 CameraPos;
uniform float Time;
uniform float Mode;
uniform float Opacity;

in vec4 vertexColor;
in vec3 spherePos;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 31.73);
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
    float amplitude = 0.55;
    for (int i = 0; i < 4; ++i) {
        value += noise(p) * amplitude;
        p = p * 2.03 + vec3(7.1, 11.7, 5.3);
        amplitude *= 0.48;
    }
    return value;
}

bool intersectSphere(vec3 rayOrigin, vec3 rayDirection, out float nearT, out float farT) {
    float b = dot(rayOrigin, rayDirection);
    float c = dot(rayOrigin, rayOrigin) - 1.0;
    float h = b * b - c;
    if (h < 0.0) return false;
    h = sqrt(h);
    nearT = -b - h;
    farT = -b + h;
    return true;
}

void main() {
    vec3 surface = normalize(spherePos);
    vec3 rayDirection = normalize(surface - CameraPos);
    float nearT;
    float farT;
    if (!intersectSphere(CameraPos, rayDirection, nearT, farT)) discard;
    nearT = max(nearT, 0.0);
    float thickness = max(farT - nearT, 0.0);
    float impact = length(cross(CameraPos, rayDirection));
    float edge = smoothstep(1.02, 0.82, impact);
    vec3 viewDirection = normalize(CameraPos - surface);
    float fresnel = pow(1.0 - abs(dot(surface, viewDirection)), 1.7);

    // Two unrelated flow fields prevent a mechanical single-axis rotation.
    vec3 slowFlow = surface * 2.7;
    slowFlow += vec3(Time * 0.19, -Time * 0.27, Time * 0.13);
    slowFlow.xz += vec2(
        sin(surface.y * 4.2 - Time * 0.8),
        cos(surface.y * 3.6 + Time * 0.63)
    ) * 0.34;
    float broad = fbm(slowFlow);

    vec3 fastFlow = surface.zxy * 5.1;
    fastFlow += vec3(-Time * 0.73, Time * 0.41, Time * 0.58);
    float detail = fbm(fastFlow);
    float folded = sin((surface.x - surface.z) * 7.0 + broad * 6.2 - Time * 2.4) * 0.5 + 0.5;
    float flowing = broad * 0.56 + detail * 0.28 + folded * 0.16;

    vec3 color;
    float alpha;

    if (Mode < 0.5) {
        // Deep translucent volume; standard alpha blend.
        color = mix(vec3(0.055, 0.035, 0.16), vec3(0.20, 0.13, 0.39), broad);
        alpha = (0.038 + thickness * 0.032 + broad * 0.055 + fresnel * 0.025) * edge;
    } else if (Mode < 1.5) {
        // Broad smoke-liquid flow inside the sphere.
        float cloud = smoothstep(0.38, 0.78, flowing);
        color = mix(vec3(0.20, 0.14, 0.42), vec3(0.46, 0.43, 0.82), cloud);
        alpha = (0.026 + cloud * 0.105 + fresnel * 0.026) * edge;
    } else if (Mode < 2.5) {
        // Broken, noisy shell highlights. Never forms a hard glass outline.
        float broken = smoothstep(0.52, 0.80, flowing + fresnel * 0.20);
        float cut = smoothstep(0.24, 0.62, detail);
        color = mix(vec3(0.085, 0.012, 0.31), vec3(0.34, 0.065, 0.72), broken);
        alpha = (0.095 + broken * cut * 0.64 + fresnel * cut * 0.22) * edge;
    } else if (Mode < 3.5) {
        // Small white-violet singularity and projectile.
        float center = smoothstep(1.0, 0.03, impact);
        color = mix(vec3(0.52, 0.48, 1.0), vec3(0.96, 0.985, 1.0), center);
        alpha = (0.16 + center * 0.72 + broad * 0.16) * edge;
    } else {
        // One-shot expanding impact sphere.
        float broken = smoothstep(0.45, 0.82, flowing);
        color = mix(vec3(0.28, 0.22, 0.72), vec3(0.72, 0.73, 1.0), broken);
        alpha = (0.028 + broken * 0.15 + fresnel * 0.12) * edge;
    }

    alpha *= Opacity * vertexColor.a;
    if (alpha < 0.003) discard;
    fragColor = vec4(color * vertexColor.rgb, clamp(alpha, 0.0, 0.96));
}
