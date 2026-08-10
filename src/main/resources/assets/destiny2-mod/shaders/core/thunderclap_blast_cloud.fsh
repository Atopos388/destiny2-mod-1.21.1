#version 150

uniform float Time;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 34.5);
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
    float amplitude = 0.58;
    for (int octave = 0; octave < 4; ++octave) {
        value += noise(p) * amplitude;
        p = p * 2.06 + vec2(5.3, 9.1);
        amplitude *= 0.47;
    }
    return value;
}

void main() {
    vec2 centered = texCoord * 2.0 - 1.0;
    float radial = length(centered);
    vec2 flow = centered * 2.15 + vec2(-Time * 0.52, Time * 0.31);
    float broad = fbm(flow);
    float detail = fbm(flow * 2.25 + vec2(Time * 0.41, -Time * 0.63));
    float disturbedRadius = radial + (broad - 0.5) * 0.22 + (detail - 0.5) * 0.08;
    float feather = 1.0 - smoothstep(0.42, 1.04, disturbedRadius);
    feather *= feather;
    float cloud = smoothstep(0.24, 0.77, broad * 0.62 + detail * 0.48);
    float core = 1.0 - smoothstep(0.0, 0.62, radial);
    float rim = smoothstep(0.54, 0.80, disturbedRadius) * (1.0 - smoothstep(0.80, 1.07, disturbedRadius));

    vec3 arcBlue = vec3(0.035, 0.24, 0.92);
    vec3 iceBlue = vec3(0.20, 0.76, 1.42);
    vec3 whiteBlue = vec3(0.82, 0.96, 1.36);
    vec3 color = mix(arcBlue, iceBlue, broad);
    color = mix(color, whiteBlue, core * 0.68 + rim * 0.18);

    float density = feather * (0.12 + cloud * 0.72 + core * 0.44) + rim * 0.10;
    float alpha = vertexColor.a * density;
    if (alpha < 0.003) discard;
    fragColor = vec4(color * vertexColor.rgb, clamp(alpha, 0.0, 0.48));
}
