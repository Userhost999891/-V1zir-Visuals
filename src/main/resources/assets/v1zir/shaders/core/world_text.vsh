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
out vec4 plateShape;
out vec2 shine;

// Glyphs: UV2.x low byte = edge shift * 256 + 128 (positive is bolder), high byte (signed) = place
// across the shine band in 1/16 half widths; UV2.y bits 0-7 = softness * 256, bits 8-12 = shine * 31.
// Plates (UV2.y + 8192): UV2.x = aspect ratio * 256, UV2.y bits 0-5 = corner radius * 63 and
// bits 6-11 = softness * 8, both in half heights; UV0 is the position in half heights from the centre.
// Either kind adds 16384 to UV2.y on additive layers.
void main() {
	gl_Position = WorldProj * WorldView * vec4(Position, 1.0);
	texCoord0 = UV0;
	vertexColor = Color;
	int low = UV2.x & 255;
	bool plate = (UV2.y & 8192) != 0;
	glyphStyle = vec3(float(low - 128) / 256.0, float(UV2.y & 255) / 256.0, UV2.y >= 16384 ? 1.0 : 0.0);
	plateShape = plate
		? vec4(float(UV2.x) / 256.0, float(UV2.y & 63) / 63.0, float((UV2.y >> 6) & 63) / 8.0, 1.0)
		: vec4(0.0);
	// (UV2.x - low) is a multiple of 256, so the division is exact for negative places too.
	shine = plate ? vec2(0.0) : vec2(float((UV2.x - low) / 256) / 16.0, float((UV2.y >> 8) & 31) / 31.0);
}
