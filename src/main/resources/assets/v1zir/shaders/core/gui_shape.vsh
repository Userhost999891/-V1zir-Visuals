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

void main() {
	gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
	vertexColor = Color;
	localPos = UV0;
	// half size, radius and softness are stored as 1/8 GUI pixel fixed point
	shape = vec4(vec2(UV1) / 8.0, float(UV2.x) / 8.0, float(UV2.y) / 8.0);
	// thickness (Normal.x * 16), mode and corner mask (small integers)
	style = vec3(Normal.x * 16.0, floor(Normal.y * 127.0 + 0.5), floor(Normal.z * 127.0 + 0.5));
}
