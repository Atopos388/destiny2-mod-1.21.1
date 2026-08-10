#version 150

uniform float Time;
uniform float Progress;
uniform float Thickness;
uniform float Opacity;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 centered = texCoord * 2.0 - 1.0;
    float radial = length(centered);
    float angle = atan(centered.y, centered.x);
    float distortion = sin(angle * 11.0 - Time * 4.2) * 0.010 +
        sin(angle * 19.0 + Time * 2.7) * 0.006;
    float distanceToBand = abs(radial + distortion - Progress);
    float outer = 1.0 - smoothstep(Thickness * 0.42, Thickness, distanceToBand);
    float inner = 1.0 - smoothstep(0.0, Thickness * 0.38, distanceToBand);
    float featheredBand = max(outer - inner * 0.34, 0.0);
    float broken = 0.76 + (sin(angle * 7.0 + Time * 3.1) * 0.5 + 0.5) * 0.24;

    vec3 deep = vec3(0.24, 0.045, 0.58);
    vec3 pale = vec3(0.72, 0.43, 1.0);
    vec3 color = mix(deep, pale, 1.0 - smoothstep(0.0, Thickness, distanceToBand));
    color *= vertexColor.rgb;
    float alpha = featheredBand * broken * Opacity * vertexColor.a;
    if (alpha < 0.002) discard;
    fragColor = vec4(color, clamp(alpha, 0.0, 0.42));
}
