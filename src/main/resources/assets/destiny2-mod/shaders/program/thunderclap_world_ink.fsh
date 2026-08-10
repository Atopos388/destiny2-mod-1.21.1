#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Strength;
uniform float Seed;
uniform int CopyOnly;

in vec2 texCoord;
out vec4 fragColor;

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec4 source = texture(DiffuseSampler, texCoord);
    if (CopyOnly != 0) {
        fragColor = source;
        return;
    }

    vec2 texel = 1.0 / max(OutSize, vec2(1.0));
    float tl = luminance(texture(DiffuseSampler, texCoord + texel * vec2(-1.0, -1.0)).rgb);
    float tc = luminance(texture(DiffuseSampler, texCoord + texel * vec2( 0.0, -1.0)).rgb);
    float tr = luminance(texture(DiffuseSampler, texCoord + texel * vec2( 1.0, -1.0)).rgb);
    float ml = luminance(texture(DiffuseSampler, texCoord + texel * vec2(-1.0,  0.0)).rgb);
    float mr = luminance(texture(DiffuseSampler, texCoord + texel * vec2( 1.0,  0.0)).rgb);
    float bl = luminance(texture(DiffuseSampler, texCoord + texel * vec2(-1.0,  1.0)).rgb);
    float bc = luminance(texture(DiffuseSampler, texCoord + texel * vec2( 0.0,  1.0)).rgb);
    float br = luminance(texture(DiffuseSampler, texCoord + texel * vec2( 1.0,  1.0)).rgb);

    float gx = -tl - 2.0 * ml - bl + tr + 2.0 * mr + br;
    float gy = -tl - 2.0 * tc - tr + bl + 2.0 * bc + br;
    float edge = smoothstep(0.10, 0.34, length(vec2(gx, gy)));

    float gray = luminance(source.rgb);
    gray = smoothstep(0.10, 0.88, gray);
    float poster = floor(gray * 5.0 + 0.5) / 5.0;
    gray = mix(gray, poster, 0.58);

    float grain = hash21(floor(texCoord * OutSize * 0.52) + floor(Seed));
    float paperGrain = (grain - 0.5) * 0.055;
    vec3 inkWorld = vec3(clamp(gray + paperGrain - edge * 0.82, 0.0, 1.0));
    // Bright areas retain a slightly warm paper-white instead of clipping to
    // a sterile digital white; all scene chroma is still removed.
    inkWorld *= vec3(1.0, 0.992, 0.965);
    fragColor = vec4(mix(source.rgb, inkWorld, clamp(Strength, 0.0, 1.0)), source.a);
}
