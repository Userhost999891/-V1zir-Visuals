#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec2 texCoord;
out vec2 screenUv;
out vec4 vertexColor;
out vec2 localPos;
out vec4 shape;
out float cornerMask;

void main() {
	gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
	// UV0 is the position relative to the rectangle's centre, UV1 its half size and UV2.x its corner radius
	// (1/8 GUI pixel fixed point), as for gui_shape.
	localPos = UV0;
	shape = vec4(vec2(UV1) / 8.0, float(UV2.x) / 8.0, 0.0);
	cornerMask = floor(Normal.z * 127.0 + 0.5);
	screenUv = localPos / max(shape.xy * 2.0, vec2(1e-3)) + 0.5;
	// The wallpaper texture has its origin at the bottom.
	texCoord = vec2(screenUv.x, 1.0 - screenUv.y);
	vertexColor = Color;
}
