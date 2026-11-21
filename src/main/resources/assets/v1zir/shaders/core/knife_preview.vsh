#version 150

#moj_import <v1zir:knife_preview.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec3 viewPos;
out vec3 viewNormal;
out vec2 pattern;
out vec4 tint;
flat out ivec2 surface;

void main() {
	vec4 view = PreviewModelView * vec4(Position, 1.0);
	gl_Position = PreviewProj * view;
	viewPos = view.xyz;
	viewNormal = mat3(PreviewModelView) * Normal;
	pattern = UV0;
	tint = Color;
	// The finish comes with the draw: one vertex buffer per knife serves every finish.
	surface = ivec2(UV1.x, int(PreviewParams.x + 0.5));
}
