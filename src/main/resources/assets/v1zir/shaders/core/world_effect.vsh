#version 150

layout(std140) uniform WorldParams {
	mat4 WorldProj;
	mat4 WorldView;
	vec4 WorldTime; // x = seconds
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord0;
out vec4 vertexColor;
// Camera-relative position: the view ray, for the water ripples.
out vec3 relPos;

void main() {
	gl_Position = WorldProj * WorldView * vec4(Position, 1.0);
	texCoord0 = UV0;
	vertexColor = Color;
	relPos = Position;
}
