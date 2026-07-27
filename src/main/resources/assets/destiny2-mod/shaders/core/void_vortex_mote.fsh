#version 150

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float distanceFromCenter = length(texCoord - vec2(0.5)) * 2.0;
    float soft = 1.0 - smoothstep(0.12, 1.0, distanceFromCenter);
    soft *= soft;
    float alpha = vertexColor.a * soft;
    if (alpha < 0.003) discard;
    fragColor = vec4(vertexColor.rgb, alpha);
}
