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
    float amplitude = 0.56;
    for (int i = 0; i < 4; ++i) {
        value += noise(p) * amplitude;
        p = p * 2.03 + vec3(5.7, 9.1, 12.3);
        amplitude *= 0.47;
    }
    return value;
}

void main() {
    vec3 surface = normalize(spherePos);
    vec3 viewDirection = normalize(CameraPos - surface);
    float facing = abs(dot(surface, viewDirection));
    float rim = pow(1.0 - facing, 1.45);
    float coarse = fbm(surface * 3.7 + vec3(Time * 0.73, -Time * 1.37, Time * 0.51));
    float detail = fbm(surface * 7.6 + vec3(-Time * 1.71, Time * 0.62, Time * 1.13));

    vec3 whiteHot = vec3(0.918, 0.992, 1.0);
    vec3 paleCyan = vec3(0.56, 0.918, 1.0);
    vec3 arcBlue = vec3(0.227, 0.561, 1.0);
    vec3 deepBlue = vec3(0.09, 0.294, 0.72);

    vec3 color;
    float alpha;
    if (Mode < 0.5) {
        float core = smoothstep(0.16, 0.88, facing + detail * 0.28);
        float hotNoise = smoothstep(0.48, 0.86, coarse * 0.67 + detail * 0.43);
        color = mix(arcBlue, paleCyan, clamp(core * 0.64 + hotNoise * 0.38, 0.0, 1.0));
        color = mix(color, whiteHot, clamp(core * core * 0.74 + hotNoise * 0.24, 0.0, 0.94));
        color *= 1.18 + core * 0.86 + hotNoise * 0.32;
        alpha = (0.16 + core * 0.43 + hotNoise * 0.20 + rim * 0.14) * Opacity;
    } else if (Mode < 1.5) {
        float upperCrown = smoothstep(-0.48, -0.02, surface.y);
        float tornEdge = smoothstep(0.38, 0.80, coarse * 0.66 + detail * 0.48 + rim * 0.24);
        float innerPlasma = smoothstep(0.30, 0.74, detail + max(surface.y, 0.0) * 0.16);
        color = mix(deepBlue, arcBlue, clamp(tornEdge * 0.72 + innerPlasma * 0.42, 0.0, 1.0));
        color = mix(color, paleCyan, clamp(rim * 0.42 + innerPlasma * 0.30, 0.0, 0.72));
        color *= 0.78 + tornEdge * 0.74 + rim * 0.48;
        alpha = upperCrown * (0.012 + tornEdge * 0.29 + rim * 0.13 + innerPlasma * 0.075) * Opacity;
    } else {
        float tornVolume = smoothstep(0.55, 0.86, coarse * 0.62 + detail * 0.46 + rim * 0.14);
        float hotMass = smoothstep(0.34, 0.76, detail * 0.58 + facing * 0.42);
        float brokenShell = smoothstep(0.48, 0.82, coarse * 0.49 + detail * 0.31 + rim * 0.30);
        float groundFade = smoothstep(0.015, 0.14, surface.y);
        color = mix(deepBlue, arcBlue, clamp(tornVolume * 0.66 + hotMass * 0.38, 0.0, 1.0));
        color = mix(color, paleCyan, clamp(hotMass * 0.28 + rim * 0.22, 0.0, 0.48));
        color = mix(color, whiteHot, clamp(hotMass * hotMass * 0.08, 0.0, 0.12));
        color *= 0.88 + tornVolume * 0.94 + hotMass * 0.34 + rim * 0.28;
        alpha = groundFade * (tornVolume * 0.28 + hotMass * 0.075 + rim * 0.14) * brokenShell * Opacity;
    }

    alpha *= vertexColor.a;
    if (alpha < 0.004) discard;
    fragColor = vec4(color * vertexColor.rgb, clamp(alpha, 0.0, 0.94));
}
