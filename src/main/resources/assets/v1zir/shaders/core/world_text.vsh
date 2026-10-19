#version 150

layout(std140) uniform WorldParams {
	mat4 WorldProj;
	mat4 WorldView;
	vec4 WorldTime;
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

out vec2 texCoord0;
out vec4 vertexColor;
out vec3 glyphStyle;

// UV2.x = edge shift * 256 (positive is bolder), UV2.y = softness * 256, plus 16384 for additive layers.
void main() {
	gl_Position = WorldProj * WorldView * vec4(Position, 1.0);
	texCoord0 = UV0;
	vertexColor = Color;
	glyphStyle = vec3(float(UV2.x) / 256.0, float(UV2.y & 16383) / 256.0, UV2.y >= 16384 ? 1.0 : 0.0);
}
