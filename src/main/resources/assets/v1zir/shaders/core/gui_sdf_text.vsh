#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

out vec2 texCoord0;
out vec4 vertexColor;
out vec2 glyphStyle;

void main() {
	gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
	texCoord0 = UV0;
	vertexColor = Color;
	glyphStyle = vec2(UV2) / 256.0;
}
