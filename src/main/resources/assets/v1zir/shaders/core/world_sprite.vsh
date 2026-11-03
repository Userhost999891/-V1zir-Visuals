#version 150

layout(std140) uniform WorldParams {
	mat4 WorldProj;
	mat4 WorldView;
	vec4 WorldTime;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;

out vec4 vertexColor;
out vec2 texCoord0;
out vec4 uvRect;

// UV1 and UV2 carry the sprite's region of the atlas (u0, v0 and u1, v1, times 16384).
void main() {
	gl_Position = WorldProj * WorldView * vec4(Position, 1.0);
	vertexColor = Color;
	texCoord0 = UV0;
	uvRect = vec4(vec2(UV1), vec2(UV2)) / 16384.0;
}
