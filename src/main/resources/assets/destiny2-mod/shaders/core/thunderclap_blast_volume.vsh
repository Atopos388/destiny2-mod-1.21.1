#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 ColorModulator;
uniform float Time;

out vec2 texCoord;
out vec4 vertexColor;

void main() {
    float waveA = sin(UV0.x * 23.0 + UV0.y * 31.0 + Time * 7.0);
    float waveB = sin(UV0.x * 37.0 - UV0.y * 19.0 - Time * 5.3);
    float amount = Color.a * 0.018;
    vec3 displaced = Position + vec3(waveA, waveB, waveA - waveB) * amount;
    gl_Position = ProjMat * ModelViewMat * vec4(displaced, 1.0);
    texCoord = UV0;
    vertexColor = Color * ColorModulator;
}
