#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <v1zir:sky_params.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec3 viewRay;
out vec2 screenUv;
out vec4 vertexColor;

void main() {
	gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
	// UV0 spans the screen 0..1 (top-left origin); rebuild a ray for the virtual camera.
	vec2 ndc = vec2(UV0.x * 2.0 - 1.0, 1.0 - UV0.y * 2.0);
	vec4 nearPoint = InvViewProj * vec4(ndc, -1.0, 1.0);
	vec4 farPoint = InvViewProj * vec4(ndc, 1.0, 1.0);
	viewRay = farPoint.xyz / farPoint.w - nearPoint.xyz / nearPoint.w;
	screenUv = UV0;
	vertexColor = Color;
}
