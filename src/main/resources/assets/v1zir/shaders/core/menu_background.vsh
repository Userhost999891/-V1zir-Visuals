#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord;
out vec2 screenUv;
out vec4 vertexColor;

void main() {
	gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
	// UV0 spans the screen 0..1 from the top-left; the sky texture has its origin at the bottom.
	screenUv = UV0;
	texCoord = vec2(UV0.x, 1.0 - UV0.y);
	vertexColor = Color;
}
