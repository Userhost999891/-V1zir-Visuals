#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec4 vertexColor;
out vec2 localPos;
out vec4 shape;
out vec3 style;
out vec4 uvRect;
out float flags;

void main() {
	gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
	vertexColor = Color;
	localPos = UV0;
	// Every corner sits 2 px (ImageElement.PAD) past the image, so the half size is the same at all four.
	float radius = (floor(Normal.x * 127.0 + 0.5) * 128.0 + floor(Normal.y * 127.0 + 0.5)) / 8.0;
	shape = vec4(abs(UV0) - 2.0, radius, 0.0);
	float bits = floor(Normal.z * 127.0 + 0.5);
	// A fill; the corner mask in the low 4 bits, the flags above.
	style = vec3(0.0, 0.0, mod(bits, 16.0));
	flags = floor(bits / 16.0);
	uvRect = vec4(vec2(UV1), vec2(UV2)) / 16384.0;
}
