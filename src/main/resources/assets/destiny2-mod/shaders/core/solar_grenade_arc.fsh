#version 150

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float across = abs(texCoord.x * 2.0 - 1.0);
    float softEdge = 1.0 - smoothstep(0.08, 1.0, across);
    softEdge *= softEdge;
    float alpha = vertexColor.a * softEdge;
    if (alpha < 0.002) discard;
    fragColor = vec4(vertexColor.rgb, alpha);
}
